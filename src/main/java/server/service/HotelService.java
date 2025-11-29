package server.service;

import server.model.*;
import server.repository.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class HotelService {

    private final RoomRepository roomRepo;
    private final ReservationRepository resRepo;
    private final PaymentRepository payRepo;    
    private final ScheduleRepository scheduleRepo;
    private static final Object LOCK = new Object();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public HotelService(){
        this.roomRepo = new RoomRepository();
        this.resRepo = new ReservationRepository();
        this.payRepo = new PaymentRepository();
        this.scheduleRepo = new ScheduleRepository();
        startAutoCancelScheduler();
    }
    
    private void startAutoCancelScheduler() {
                
        checkAndCancelUnpaidReservations();
        scheduleDailyTask();
    }

    public List<Room> getAllRooms() {
        return roomRepo.findAll();
    }

    public Room getRoom(String roomNum) {
        return roomRepo.findByNumber(roomNum);
    }

    public List<Reservation> getReservationsByName(String name) {
        return resRepo.findAll().stream()
                .filter(r -> r.getGuestName().equals(name))
                .collect(Collectors.toList());
    }

    /**
     * 지정한 날짜 범위의 일별 객실 매출 합계를 반환
     * 결제 정보가 "Paid"인 예약만 포함
     * @param startDate yyyy-MM-dd
     * @param endDate yyyy-MM-dd
     * @return Map<LocalDate,Integer> (LinkedHashMap 날짜 순서 유지)
     */
    public Map<LocalDate, Integer> getRoomSalesByDateRange(String startDate, String endDate) {
        DateTimeFormatter dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        // 입력으로 받은 문자열 날짜(startDate, endDate)를 LocalDate로 변환합니다.
        // 여기서는 yyyy-MM-dd 포맷을 가정합니다. 파싱 실패 시 DateTimeParseException이 발생합니다.
        LocalDate start = LocalDate.parse(startDate, dateOnlyFormatter);
        LocalDate end = LocalDate.parse(endDate, dateOnlyFormatter);

        // 날짜(키) -> 매출(값) 매핑을 만들되, 날짜 순서를 유지하기 위해 LinkedHashMap을 사용합니다.
        // 먼저 요청된 범위의 모든 날짜를 0으로 초기화하면 이후 누적 합산이 쉬워집니다.
        Map<LocalDate, Integer> salesMap = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) salesMap.put(d, 0);

        // 저장소에서 모든 예약을 읽어 옵니다. (CSV 기반 Repository의 현재 스냅샷)
        List<Reservation> all = resRepo.findAll();

        for (Reservation r : all) {
            try {
                // ReservationStatus가 'Confirmed'인 예약만 포함
                if (r.getReservationStatus() == null || !r.getReservationStatus().equalsIgnoreCase("Confirmed")) continue;
                LocalDate in = LocalDate.parse(r.getCheckInDate(), dateOnlyFormatter);
                LocalDate out = LocalDate.parse(r.getCheckOutDate(), dateOnlyFormatter);

                // 해당 예약이 사용한 방의 가격을 조회합니다. RoomRepository에서 방 번호로 찾습니다.
                // 방 정보가 없으면 가격을 0으로 처리(안정성 확보).
                Room room = roomRepo.findByNumber(r.getRoomNumber());
                int price = (room != null) ? room.getPrice() : 0;

                // 예약은 [checkIn, checkOut) 구간의 각 하룻밤에 대해 매출을 발생시킵니다.
                // 따라서 checkIn부터 (checkOut - 1)일까지 반복하며 해당 날짜가 요청 범위에 포함되면 해당 날짜의 합계에 더합니다.
                for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1)) {
                    // salesMap에 날짜 키가 존재하고, 요청한 범위(start..end)에 속한 날짜만 누적합니다.
                    if (!d.isBefore(start) && !d.isAfter(end)) {
                        salesMap.put(d, salesMap.getOrDefault(d, 0) + price);
                    }
                }
            } catch (Exception ex) {
                // 파싱(날짜 형식) 오류 등 예외가 발생하면 해당 예약은 집계에서 건너뛰고 계속 진행합니다.
                // 필요시 로깅을 추가하여 데이터 문제를 추적하는 것이 좋습니다.
            }
        }
        return salesMap;
    }

    // 예약 가능한 방 타입 목록 반환
    public String getAvailableRoomTypes(String reqIn, String reqOut) {
        synchronized (LOCK) {
            List<String> availableTypes = new ArrayList<>();
            List<Reservation> allRes = resRepo.findAll();            
            List<RoomSchedule> allSched = scheduleRepo.findAll();

            // 각 타입의 대표 방번호로 가능여부 체크 
            if (isRoomAvailable("101", reqIn, reqOut, allRes, allSched)) availableTypes.add("STD");
            if (isRoomAvailable("201", reqIn, reqOut, allRes, allSched)) availableTypes.add("DLX");
            if (isRoomAvailable("301", reqIn, reqOut, allRes, allSched)) availableTypes.add("STE");

            return String.join(",", availableTypes);
        }
    }
    
    public String getRoomStatusList(String reqIn, String reqOut) {
        StringBuilder sb = new StringBuilder("ROOM_STATUS_LIST:");
        List<Room> rooms = roomRepo.findAll();
        List<Reservation> allRes = resRepo.findAll();
        List<RoomSchedule> schedules = scheduleRepo.findAll(); //객실의 스케줄 여부도 체크

        for (Room r : rooms) {
            boolean isBooked = false;

            // 스케줄 확인 (청소 중이면 예약 불가)
            for (RoomSchedule sc : schedules) {
                if (sc.getRoomNumber().equals(r.getRoomNumber())) {
                    if (isDateIncluded(reqIn, sc.getStartDate(), sc.getEndDate()) ||
                        isDateIncluded(reqOut, sc.getStartDate(), sc.getEndDate()) ||
                        isDateIncluded(sc.getStartDate(), reqIn, reqOut)) { 
                        isBooked = true;
                        break;
                    }
                }
            }

            // 예약 가능여부 확인
            if (!isBooked) {
                for (Reservation res : allRes) {
                    if (res.getRoomNumber().equals(r.getRoomNumber())) {
                        // 체크아웃 되었다면 예약 가능)
                        if ("CheckedOut".equals(res.getReservationStatus())) continue;
                        
                        // 날짜 겹침 확인
                        if (isDateOverlapping(reqIn, reqOut, res.getCheckInDate(), res.getCheckOutDate())) {
                            isBooked = true;
                            break;
                        }
                    }
                }
            }

            String status = isBooked ? "BOOKED" : "AVAILABLE";
            
            // 포맷: 방번호,타입,가격,인원,설명,상태
            sb.append(String.format("%s,%s,%d,%d,%s,%s|", 
                    r.getRoomNumber(), r.getType(), r.getPrice(), r.getCapacity(), r.getDescription(), status));
        }
        return sb.toString();
    }

   // 예약 생성
    public String createReservationByRoomNum(String roomNum, String name, String reqIn, String reqOut, int guestNum, String phone, String request) {
        synchronized (LOCK) {
            // 1. 해당 방 번호가 실존하는지 확인
            Room room = roomRepo.findByNumber(roomNum);
            if (room == null) return null; // 없는 방

            // 2. 해당 방의 예약 가능 여부(날짜 겹침, 스케줄) 확인
            List<Reservation> allRes = resRepo.findAll();
            List<RoomSchedule> allSched = scheduleRepo.findAll();
            
            if (isRoomAvailable(roomNum, reqIn, reqOut, allRes, allSched)) {
                // 3. 예약 저장
                String nowStr = LocalDateTime.now().format(formatter);
                String resId = resRepo.add(roomNum, name, reqIn, reqOut, guestNum, phone, nowStr, request);
                return (resId != null) ? roomNum : null;
            }
            return null; // 이미 예약됨
        }
    }
    
    public boolean cancelReservation(String resId) {
        synchronized (LOCK) { return resRepo.delete(resId); }
    }

    public boolean processPayment(String resId, String method, String cardNum, String cvc, String expiry, String pw) {
        synchronized (LOCK) {
            String payId = "P-" + System.currentTimeMillis();
            String paymentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Payment newPayment = new Payment(payId, resId, method, cardNum, cvc, expiry, pw, paymentTime);
            boolean paySaved = payRepo.add(newPayment);
            if (paySaved) {
                return resRepo.updateStatus(resId, "Confirmed");
            }
            return false;
        }
    }
    
    public boolean checkIn(String resId){
        synchronized (LOCK){
            return resRepo.updateStatus(resId, "CheckedIn");
        }
    }
    
    public boolean checkOut(String resId){
        synchronized(LOCK){
            return resRepo.updateStatus(resId, "CheckedOut");
        }
    }
    
    public List<String> getReservationsWithRoomInfo(String guestName){
        List<Reservation> myReservations = resRepo.findAll().stream().filter(
        r -> r.getGuestName().equals(guestName)).collect(Collectors.toList());

        List<Room> allRooms = roomRepo.findAll();
        List<String> resultList = new ArrayList<>();
        
        for(Reservation res : myReservations){
            String resStr = res.toString();
            String roomType = "Unknown";
            int roomPrice = 0;
            
            for (Room r : allRooms){
            
                if(r.getRoomNumber().equals(res.getRoomNumber())){
                    roomType = r.getType();
                    roomPrice = r.getPrice();
                    break;
                }
                resultList.add(resStr = "," + roomType + "," + roomPrice);
            }
        } 
        return resultList;
    }
    
    private boolean isRoomAvailable(String roomNum, String reqIn, String reqOut, List<Reservation> allRes, List<RoomSchedule> allSched) {
        // 예약 겹침 확인
        for (Reservation res : allRes) {
            if (res.getRoomNumber().equals(roomNum)) {
                // 체크아웃 된 건은 무시 (예약 가능)
                if ("CheckedOut".equals(res.getReservationStatus())) continue;

                if (isDateOverlapping(reqIn, reqOut, res.getCheckInDate(), res.getCheckOutDate())) {
                    return false; // 겹침
                }
            }
        }
        
        // 스케줄(청소/수리) 겹침 확인
        for (RoomSchedule sc : allSched) {
            if (sc.getRoomNumber().equals(roomNum)) {
                if (isDateOverlapping(reqIn, reqOut, sc.getStartDate(), sc.getEndDate())) {
                    return false; // 청소/수리 중이라 예약 불가
                }
            }
        }
        
        return true;
    }

    private boolean isDateOverlapping(String reqIn, String reqOut, String existIn, String existOut) {
        // (요청 시작 < 기존 종료) AND (요청 종료 > 기존 시작)
        return (reqIn.compareTo(existOut) < 0) && (reqOut.compareTo(existIn) > 0);
    }

    private String convertCodeToType(String code) {
        if ("STD".equals(code)) return "Standard";
        if ("DLX".equals(code)) return "Deluxe";
        if ("STE".equals(code)) return "Suite";
        return code;
    }
    
    private void scheduleDailyTask(){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(18, 0, 0));
        
        if (now.isAfter(targetTime)) {
            targetTime = targetTime.plusDays(1);
        }
        
        long initialDelay = Duration.between(now, targetTime).getSeconds();
        long oneDayInSeconds = 24 * 60 * 60; 
        
        // 중복 실행 방지를 위해 Runnable 작업 정의
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (LOCK) { 
                System.out.println("[System] 18:00 미보장 예약 자동취소 점검 시작");
                checkAndCancelUnpaidReservations();
            }
        }, initialDelay, oneDayInSeconds, TimeUnit.SECONDS);
    }
    
    private void checkAndCancelUnpaidReservations() {
        List<Reservation> all = resRepo.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Reservation r : all) {
            if (!"Unpaid".equals(r.getReservationStatus())) continue;

            try {
                // 예약 생성 시간 파싱
                LocalDateTime createdAt = LocalDateTime.parse(r.getCreatedAt(), formatter);
                LocalDateTime deadline;

                // [조건] 17시 이전 예약 -> 당일 18시 마감  
                // [조건] 17시 이후 예약 -> 다음날 18시 마감
                if (createdAt.getHour() < 17) {
                    deadline = createdAt.toLocalDate().atTime(18, 0, 0);
                } else {
                    deadline = createdAt.plusDays(1).toLocalDate().atTime(18, 0, 0);
                }

                // 현재 시간이 마감 시간을 지났으면 삭제
                if (now.isAfter(deadline)) {
                    System.out.println("[자동취소] 기한 만료! ID: " + r.getReservationId() + 
                            " (생성: " + r.getCreatedAt() + " / 마감: " + deadline + ")");
                    resRepo.delete(r.getReservationId());
                }

            } catch (Exception e) {
                System.out.println("날짜 파싱 오류 (ID: " + r.getReservationId() + "): " + e.getMessage());
            }
        }
    }   
    
    public String getRoomDashboard(String targetDate) {
        StringBuilder sb = new StringBuilder("DASHBOARD_LIST:");
        List<Room> rooms = roomRepo.findAll();
        List<Reservation> reservations = resRepo.findAll();
        List<RoomSchedule> schedules = scheduleRepo.findAll();
        
        for (Room r : rooms) {
            String status = "Empty";
            String guestName = "-";
            String resId = "-";
            int guestNum = 0; 
            String phone = "-";
            String inDate = "-"; 
            String outDate = "-";
            String note = r.getDescription();
            String detail = "-"; // 요청사항 또는 스케줄 내용

            // 1. 스케줄 확인 (청소, 수리 등) -> 예약보다 우선순위 높음
            for (RoomSchedule sc : schedules) {
                if (sc.getRoomNumber().equals(r.getRoomNumber())) {
                    if (isDateIncluded(targetDate, sc.getStartDate(), sc.getEndDate())) {
                        status = sc.getType();    // Cleaning, Maintenance...
                        guestName = sc.getNote(); // 이름 자리에 메모 표시
                        resId = sc.getScheduleId();
                        inDate = sc.getStartDate();
                        outDate = sc.getEndDate();
                        detail = "일정: " + sc.getNote();
                        break; 
                    }
                }
            }

            // 2. 예약 확인 (스케줄이 없을 때만)
            if ("Empty".equals(status)) {
                for (Reservation res : reservations) {
                    if (res.getRoomNumber().equals(r.getRoomNumber())) {
                        // 날짜 범위 확인: 입실일 <= 조회일 < 퇴실일
                        // (퇴실일 당일은 아직 체크아웃 전이라도, 숙박의 관점에서는 오후에 빈 방이 됨)
                        if (isDateIncluded(targetDate, res.getCheckInDate(), res.getCheckOutDate()) 
                                && targetDate.compareTo(res.getCheckOutDate()) < 0) {
                            
                                status = res.getReservationStatus(); 
                                guestName = res.getGuestName();
                                resId = res.getReservationId();
                                guestNum = res.getGuestNum();
                                phone = res.getPhoneNumber();
                                inDate = res.getCheckInDate();
                                outDate = res.getCheckOutDate();
                                detail = res.getCustomerRequest(); // 요청사항
                                break; 
                        }
                    }
                }
            }
            
            sb.append(String.format("%s,%s,%d,%s,%s,%s,%d,%s,%s,%s,%s,%s|", 
                    r.getRoomNumber(), r.getType(), r.getPrice(), status, guestName, 
                    resId, guestNum, phone, inDate, outDate, note, detail));
        }
        return sb.toString();
    }
    
    public boolean addRoom(String num, String type, int price, int cap, String desc) {
        return roomRepo.add(new Room(num, type, price, cap, desc));
    }

    public boolean updateRoom(String num, String type, int price, int cap, String desc) {
        return roomRepo.update(new Room(num, type, price, cap, desc));
    }
    
    private boolean isDateIncluded(String target, String start, String end) {
        return target.compareTo(start) >= 0 && target.compareTo(end) <= 0;
    }
    
    public boolean updateReservationRequest(String resId, String newRequest) {
        synchronized (LOCK) {
            return resRepo.updateRequest(resId, newRequest);
        }
    }
    
    public boolean updateReservationStatus(String resId, String newStatus) {
        synchronized (LOCK) {
            return resRepo.updateStatus(resId, newStatus);
        }
    }
    
    public ReservationRepository getReservationRepository(){
        return resRepo;
    }
    
    public RoomRepository getRoomRepository(){
        return roomRepo;
    }
}