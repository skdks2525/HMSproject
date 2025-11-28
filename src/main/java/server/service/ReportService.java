
package server.service;

import server.model.Reservation;
import server.model.Room;
import server.repository.ReservationRepository;
import server.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;

public class ReportService {

    /**
     * 과거 점유율 요청 핸들러
     * - 지정 기간(start~end) 동안 날짜별, 객실타입별(스탠다드/디럭스/스위트) 점유율과 전체 평균을 계산
     * - ReservationStatus가 Confirmed인 예약만 집계
     * - 각 날짜별로 해당 타입 객실 중 예약된 객실 수/전체 객실 수로 점유율 산출
     * - 응답 포맷: PAST_OCCUPANCY:평균점유율|날짜,스탠다드,디럭스,스위트,평균;...
     */
    public String handlePastOccupancyRequest(String start, String end) {
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        List<Room> rooms = roomRepository.findAll();
        List<Reservation> reservations = reservationRepository.findConfirmedInPeriod(start, end);
        // 날짜별, 타입별 점유율 집계
        List<String> dateRows = new ArrayList<>();
        double sumAll = 0;
        int dayCount = 0;
        int stdTotal = (int) rooms.stream().filter(r -> r.getType().equals("Standard")).count(); // 스탠다드 객실 수
        int dlxTotal = (int) rooms.stream().filter(r -> r.getType().equals("Deluxe")).count();   // 디럭스 객실 수
        int steTotal = (int) rooms.stream().filter(r -> r.getType().equals("Suite")).count();    // 스위트 객실 수
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            int std = 0, dlx = 0, ste = 0;
            // 해당 날짜에 예약된 객실을 타입별로 카운트
            for (Reservation r : reservations) {
                if (!d.isBefore(LocalDate.parse(r.getCheckInDate())) && !d.isAfter(LocalDate.parse(r.getCheckOutDate()))) {
                    String type = rooms.stream().filter(room -> room.getRoomNumber().equals(r.getRoomNumber())).map(Room::getType).findFirst().orElse("");
                    if (type.equals("Standard")) std++;
                    else if (type.equals("Deluxe")) dlx++;
                    else if (type.equals("Suite")) ste++;
                }
            }
            // 타입별 점유율(%) 계산
            double stdRate = stdTotal > 0 ? std * 100.0 / stdTotal : 0.0;
            double dlxRate = dlxTotal > 0 ? dlx * 100.0 / dlxTotal : 0.0;
            double steRate = steTotal > 0 ? ste * 100.0 / steTotal : 0.0;
            // 전체 평균 점유율(모든 객실 기준)
            double avg = (std + dlx + ste) / (double)(stdTotal + dlxTotal + steTotal) * 100.0;
            sumAll += avg;
            dayCount++;
            // 날짜, 스탠다드, 디럭스, 스위트, 평균 순으로 표 데이터 생성
            dateRows.add(d + "," + String.format("%.2f", stdRate) + "," + String.format("%.2f", dlxRate) + "," + String.format("%.2f", steRate) + "," + String.format("%.2f", avg));
        }
        double avgAll = dayCount > 0 ? sumAll / dayCount : 0.0;
        // 응답: 평균점유율|날짜,스탠다드,디럭스,스위트,평균;...
        return "PAST_OCCUPANCY:" + String.format("%.2f", avgAll) + "|" + String.join(";", dateRows);
    }

    // 개별 객실의 점유율(%) 계산
    private double getRoomOccupancyRate(Room room, List<Reservation> reservations, LocalDate start, LocalDate end) {
    /**
     * 개별 객실의 점유율(%) 계산
     * - 지정 기간 내 해당 객실이 예약된 일수 / 전체 일수 × 100
     * - Confirmed 예약만 집계
     */
    private double getRoomOccupancyRate(Room room, List<Reservation> reservations, LocalDate start, LocalDate end) {
        long totalDays = end.toEpochDay() - start.toEpochDay() + 1;
        long reservedDays = 0;
        String roomNum = room.getRoomNumber();
        for (Reservation r : reservations) {
            if (!roomNum.equals(r.getRoomNumber())) continue;
            LocalDate resStart = LocalDate.parse(r.getCheckInDate());
            LocalDate resEnd = LocalDate.parse(r.getCheckOutDate());
            // 예약과 기간이 겹치는 일수만큼 합산
            LocalDate overlapStart = resStart.isAfter(start) ? resStart : start;
            LocalDate overlapEnd = resEnd.isBefore(end) ? resEnd : end;
            if (!overlapStart.isAfter(overlapEnd)) {
                reservedDays += (overlapEnd.toEpochDay() - overlapStart.toEpochDay() + 1);
            }
        }
        return totalDays > 0 ? (reservedDays * 100.0 / totalDays) : 0.0;
    }

        // 현재 점유율 요청 핸들러: 오늘 투숙 중인 객실 표
        public String handleCurrentOccupancyRequest() {
        /**
         * 현재 점유율 요청 핸들러
         * - 오늘 날짜 기준 투숙 중인 Confirmed 예약 정보 표 반환
         * - 각 객실별로 객실번호, 예약ID, 체크인/체크아웃, 투숙인원 반환
         * - 응답 포맷: CURRENT_OCCUPANCY:roomNumber,guestId,checkIn,checkOut,guestNum;...
         */
        List<Map<String, Object>> rows = getCurrentOccupancyReport();
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(row.get("roomNumber") + "," + row.get("guestId") + "," + row.get("checkIn") + "," + row.get("checkOut") + "," + row.get("guestNum"));
        }
        return "CURRENT_OCCUPANCY:" + String.join(";", out);
        }

        // 미래 점유율 예측 요청 핸들러: 기간(시작~끝)별, 타입별 예측 표 + 전체 평균
    /**
     * 미래 점유율 예측 요청 핸들러
     * - 지정 기간(start~end) 동안 날짜별, 객실타입별(스탠다드/디럭스/스위트) 점유율과 전체 평균을 예측
     * - 실제 Confirmed 예약이 있는 날짜는 실제 예약 기준으로 점유율 계산
     * - 예약이 없는 날짜는 방학 시즌(7~9월, 1~3월)엔 40~80%, 그 외엔 10~30% 랜덤 점유율 생성
     * - 응답 포맷: FUTURE_OCCUPANCY:평균점유율|날짜,스탠다드,디럭스,스위트,평균;...
     */
    public String handleFutureOccupancyRequest(String start, String end) {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            List<Room> rooms = roomRepository.findAll();
            Map<String, String> roomTypeMap = new HashMap<>();
            for (Room r : rooms) roomTypeMap.put(r.getRoomNumber(), r.getType());
            List<Reservation> allConfirmed = reservationRepository.findConfirmedInPeriod(start, end);
            int stdTotal = (int) rooms.stream().filter(r -> r.getType().equals("Standard")).count();
            int dlxTotal = (int) rooms.stream().filter(r -> r.getType().equals("Deluxe")).count();
            int steTotal = (int) rooms.stream().filter(r -> r.getType().equals("Suite")).count();
            double sumAll = 0;
            int dayCount = 0;
            List<String> dateRows = new ArrayList<>();
            // 미래 점유율 예측: 실제 예약이 없는 날짜는 랜덤값, 방학 시즌은 높은 점유율로 생성
            java.util.Random rand = new java.util.Random();
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                int std = 0, dlx = 0, ste = 0;
                boolean hasReservation = false;
                // 1. 실제 예약(Confirmed)이 있는 객실은 실제 데이터로 집계
                for (Room r : rooms) {
                    String type = r.getType();
                    String roomNum = r.getRoomNumber();
                    for (Reservation res : allConfirmed) {
                        if (!roomNum.equals(res.getRoomNumber())) continue;
                        LocalDate in = LocalDate.parse(res.getCheckInDate());
                        LocalDate out = LocalDate.parse(res.getCheckOutDate());
                        if (!d.isBefore(in) && !d.isAfter(out)) {
                            hasReservation = true; // 이 날짜에 실제 예약 있음
                            // 타입별 카운트
                            if (type.equals("Standard")) std++;
                            else if (type.equals("Deluxe")) dlx++;
                            else if (type.equals("Suite")) ste++;
                            break;
                        }
                    }
                }
                // 2. 실제 예약이 없는 날은 랜덤 점유율 생성
                double stdRate, dlxRate, steRate, avg;
                if (!hasReservation) {
                    // 방학 시즌: 7~9월, 1~3월은 높은 점유율(40~80%), 그 외는 낮은 점유율(10~30%)
                    int month = d.getMonthValue();
                    boolean vacation = (month >= 7 && month <= 9) || (month >= 1 && month <= 3);
                    // 각 타입별로 방학 시즌이면 40~80%, 아니면 10~30% 랜덤값
                    stdRate = stdTotal > 0 ? (vacation ? 40 + rand.nextInt(41) : 10 + rand.nextInt(21)) : 0.0;
                    dlxRate = dlxTotal > 0 ? (vacation ? 40 + rand.nextInt(41) : 10 + rand.nextInt(21)) : 0.0;
                    steRate = steTotal > 0 ? (vacation ? 40 + rand.nextInt(41) : 10 + rand.nextInt(21)) : 0.0;
                    // 전체 평균은 타입별 객실수 가중평균
                    avg = (stdRate * stdTotal + dlxRate * dlxTotal + steRate * steTotal) / (stdTotal + dlxTotal + steTotal);
                } else {
                    // 실제 예약이 있으면 실제 점유율 계산
                    stdRate = stdTotal > 0 ? std * 100.0 / stdTotal : 0.0;
                    dlxRate = dlxTotal > 0 ? dlx * 100.0 / dlxTotal : 0.0;
                    steRate = steTotal > 0 ? ste * 100.0 / steTotal : 0.0;
                    avg = (std + dlx + ste) / (double)(stdTotal + dlxTotal + steTotal) * 100.0;
                }
                // 전체 평균 누적 및 표 데이터 추가
                sumAll += avg;
                dayCount++;
                dateRows.add(d + "," + String.format("%.2f", stdRate) + "," + String.format("%.2f", dlxRate) + "," + String.format("%.2f", steRate) + "," + String.format("%.2f", avg));
            }
            double avgAll = dayCount > 0 ? sumAll / dayCount : 0.0;
            return "FUTURE_OCCUPANCY:" + String.format("%.2f", avgAll) + "|" + String.join(";", dateRows);
        }
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    public ReportService(ReservationRepository reservationRepository, RoomRepository roomRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
    }

    /**
     * 과거 점유율 보고서: 각 객실별로 지정 기간 내 점유율(Confirmed 예약 기준) 계산
     * @param start 시작일 (포함)
     * @param end 종료일 (포함)
     * @return List<Map<String, Object>>: roomNumber, totalDays, reservedDays, occupancyRate(%)
     */
    public List<Map<String, Object>> getPastOccupancyReport(LocalDate start, LocalDate end) {
        List<Room> rooms = roomRepository.findAll();
        List<Reservation> reservations = reservationRepository.findConfirmedInPeriod(start.toString(), end.toString());
        long totalDays = end.toEpochDay() - start.toEpochDay() + 1;
        List<Map<String, Object>> report = new ArrayList<>();
        for (Room room : rooms) {
            String roomNum = room.getRoomNumber();
            // 해당 객실의 예약 중, 기간과 겹치는 일수 합산
            long reservedDays = 0;
            for (Reservation r : reservations) {
                if (!roomNum.equals(r.getRoomNumber())) continue;
                LocalDate resStart = LocalDate.parse(r.getCheckInDate());
                LocalDate resEnd = LocalDate.parse(r.getCheckOutDate());
                LocalDate overlapStart = resStart.isAfter(start) ? resStart : start;
                LocalDate overlapEnd = resEnd.isBefore(end) ? resEnd : end;
                if (!overlapStart.isAfter(overlapEnd)) {
                    reservedDays += (overlapEnd.toEpochDay() - overlapStart.toEpochDay() + 1);
                }
            }
            double occupancyRate = totalDays > 0 ? (reservedDays * 100.0 / totalDays) : 0.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomNumber", roomNum);
            row.put("totalDays", totalDays);
            row.put("reservedDays", reservedDays);
            row.put("occupancyRate", String.format("%.2f", occupancyRate));
            report.add(row);
        }
        return report;
    }

    /**
     * 현재 점유율 보고서: 오늘 날짜 기준 투숙 중인 Confirmed 예약 정보 표 반환
     * @return List<Map<String, Object>>: roomNumber, guestId, checkIn, checkOut, guestNum
     */
    public List<Map<String, Object>> getCurrentOccupancyReport() {
        LocalDate today = LocalDate.now();
        List<Reservation> reservations = reservationRepository.findConfirmedToday(today.toString());
        List<Map<String, Object>> report = new ArrayList<>();
        for (Reservation r : reservations) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomNumber", r.getRoomNumber());
            row.put("guestId", r.getReservationId());
            row.put("checkIn", r.getCheckInDate());
            row.put("checkOut", r.getCheckOutDate());
            row.put("guestNum", r.getGuestNum());
            report.add(row);
        }
        return report;
    }

    /**
     * 미래 점유율 예측 보고서: 지정한 날짜의 점유율을 과거 동일 요일의 평균 점유율로 예측
     * @param targetDate 예측할 날짜
     * @return List<Map<String, Object>>: roomNumber, predictedOccupancyRate(%)
     */
    public List<Map<String, Object>> getFutureOccupancyPrediction(LocalDate targetDate) {
        List<Room> rooms = roomRepository.findAll();
        // 과거 4주간 동일 요일 데이터로 예측
        List<LocalDate> pastDates = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            pastDates.add(targetDate.minusWeeks(i));
        }
        List<Reservation> allConfirmed = reservationRepository.findAll();
        List<Map<String, Object>> report = new ArrayList<>();
        for (Room room : rooms) {
            String roomNum = room.getRoomNumber();
            int count = 0;
            for (LocalDate d : pastDates) {
                for (Reservation r : allConfirmed) {
                    if (!"Confirmed".equalsIgnoreCase(r.getReservationStatus())) continue;
                    if (!roomNum.equals(r.getRoomNumber())) continue;
                    LocalDate in = LocalDate.parse(r.getCheckInDate());
                    LocalDate out = LocalDate.parse(r.getCheckOutDate());
                    if (!d.isBefore(in) && !d.isAfter(out)) {
                        count++;
                        break;
                    }
                }
            }
            double predicted = (count / 4.0) * 100.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomNumber", roomNum);
            row.put("predictedOccupancyRate", String.format("%.2f", predicted));
            report.add(row);
        }
        return report;
    }
}
