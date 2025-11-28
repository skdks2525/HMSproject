package server.service;

import server.model.*;
import server.repository.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class HotelService {
        public RoomRepository getRoomRepository() {
            return roomRepo;
        }
        public ReservationRepository getReservationRepository() {
            return resRepo;
        }
    private final RoomRepository roomRepo = new RoomRepository();
    private final ReservationRepository resRepo = new ReservationRepository();
    private final PaymentRepository payRepo = new PaymentRepository();
    private static final Object LOCK = new Object();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public HotelService(){
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

// [기능 1] 예약 가능한 방 타입 목록 반환
    public String getAvailableRoomTypes(String reqIn, String reqOut) {
        synchronized (LOCK) {
            List<String> availableTypes = new ArrayList<>();
            List<Reservation> allRes = resRepo.findAll();

            // 각 타입의 대표 방 하나씩만 체크 (간소화 버전)
            // 실제로는 해당 타입의 모든 방을 검사해야 함
            if (isRoomAvailable("101", reqIn, reqOut, allRes)) availableTypes.add("STD");
            if (isRoomAvailable("201", reqIn, reqOut, allRes)) availableTypes.add("DLX");
            if (isRoomAvailable("301", reqIn, reqOut, allRes)) availableTypes.add("STE");

            return String.join(",", availableTypes);
        }
    }

    // 예약 생성 (현재 시간)
    public String createReservationByType(String type, String name, String reqIn, String reqOut, int guestNum, String phone) {
        synchronized (LOCK) {
            List<Room> targetRooms = new ArrayList<>();
            for (Room r : roomRepo.findAll()) {
                if (convertCodeToType(type).equalsIgnoreCase(r.getType())) {
                    targetRooms.add(r);
                }
            }
            if (targetRooms.isEmpty()) return null;

            List<Reservation> allRes = resRepo.findAll();
            String availableRoomNum = null;

            for (Room room : targetRooms) {
                if (isRoomAvailable(room.getRoomNumber(), reqIn, reqOut, allRes)) {
                    availableRoomNum = room.getRoomNumber();
                    break;
                }
            }

            if (availableRoomNum != null) {
                // 현재 시간을 생성일로 저장
                String nowStr = LocalDateTime.now().format(formatter);
                String resId = resRepo.add(availableRoomNum, name, reqIn, reqOut, guestNum, phone, nowStr);
                return (resId != null) ? availableRoomNum : null;
            }
            return null;
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
    
    public boolean checkou(String resId){
        synchronized(LOCK){
            return resRepo.updateStatus(resId, "CheckedOut");
        }
    }
    
    private boolean isRoomAvailable(String roomNum, String reqIn, String reqOut, List<Reservation> allRes) {
        for (Reservation res : allRes) {
            if (res.getRoomNumber().equals(roomNum)) {
                // 겹치면 false (예약 불가)
                if (isDateOverlapping(reqIn, reqOut, res.getCheckInDate(), res.getCheckOutDate())) {
                    return false; 
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
        
        // 목표 시간까지 남은 초(Seconds) 계산
        long initialDelay = Duration.between(now, targetTime).getSeconds();
        long oneDayInSeconds = 24 * 60 * 60; // 24시간 주기
        
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (LOCK) { 
                System.out.println("18:00 미보장 예약 자동취소");
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
    
    public String getRoomDashboard() {
        StringBuilder sb = new StringBuilder("DASHBOARD_LIST:");
        List<Room> rooms = roomRepo.findAll();
        List<Reservation> reservations = resRepo.findAll();
        
        for (Room r : rooms) {
            String status = "Empty";
            String guestName = "-";
            String resId = "-";
            // [추가] 기본값 설정
            int guestNum = 0; 
            String phone = "-";
            
            for (Reservation res : reservations) {
                if (res.getRoomNumber().equals(r.getRoomNumber())) {
                    // 체크아웃 된 건 제외하고 현재 유효한 예약 표시
                    if (!"CheckedOut".equals(res.getReservationStatus())) {
                         status = res.getReservationStatus(); 
                         guestName = res.getGuestName();
                         resId = res.getReservationId();
                         // [추가] 정보 가져오기
                         guestNum = res.getGuestNum();
                         phone = res.getPhoneNumber();
                         break; 
                    }
                }
            }
            // 포맷: 방번호,타입,가격,상태,고객명,예약ID,인원,폰 (8개 정보)
            sb.append(String.format("%s,%s,%d,%s,%s,%s,%d,%s/", 
                    r.getRoomNumber(), r.getType(), r.getPrice(), status, guestName, resId, guestNum, phone));
        }
        return sb.toString();
    }
}