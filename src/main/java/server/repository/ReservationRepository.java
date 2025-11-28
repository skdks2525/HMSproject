package server.repository;
import server.model.*;
import java.io.*;
import java.util.*;
/**
 *
 * @author user
 */
public class ReservationRepository {
        /**
         * reservation.csv 파일의 지정한 기간 내 Confirmed 상태의 예약만 반환
         * 예약의 체크인~체크아웃 날짜와 보고서의 시작~끝 날짜의 기간이 겹치는지 확인
         * 겹치면 집계, 아니면 무시
         */
        public synchronized List<Reservation> findConfirmedInPeriod(String startDate, String endDate) {
            List<Reservation> all = findAll();
            List<Reservation> result = new ArrayList<>();
            for (Reservation r : all) {
                if (!"Confirmed".equalsIgnoreCase(r.getReservationStatus().trim())) continue;
                // 예약이 기간과 겹치는지 확인
                if (isOverlap(r.getCheckInDate(), r.getCheckOutDate(), startDate, endDate)) {
                    result.add(r);
                }
            }
            return result;
        }

        /**
         * 오늘날짜 기준 Confirmed 상태의 투숙 중 예약 반환
         */
        public synchronized List<Reservation> findConfirmedToday(String today) {
            List<Reservation> all = findAll();
            List<Reservation> result = new ArrayList<>();
            for (Reservation r : all) {
                if (!"Confirmed".equalsIgnoreCase(r.getReservationStatus().trim())) continue;
                if (isDateInRange(today, r.getCheckInDate(), r.getCheckOutDate())) {
                    result.add(r);
                }
            }
            return result;
        }

        /**
         * 지정한 날짜에 투숙 중인 Confirmed 예약 반환
         */
        public synchronized List<Reservation> findConfirmedOnDate(String date) {
            return findConfirmedToday(date);
        }

        // 날짜 겹침 여부 확인 (yyyy-MM-dd)
        private boolean isOverlap(String start1, String end1, String start2, String end2) {
            return !(end1.compareTo(start2) < 0 || start1.compareTo(end2) > 0);
        }

        // date가 [start, end] 사이에 있는지
        private boolean isDateInRange(String date, String start, String end) {
            return (date.compareTo(start) >= 0 && date.compareTo(end) <= 0);
        }
    private static final String RES_FILE_PATH = "data/reservations.csv";
    
    public synchronized List<Reservation> findAll(){
        List<Reservation> list = new ArrayList<>();
        File file = new File(RES_FILE_PATH);
        // ...existing code...
        if(!file.exists()) return list;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;    
            reader.readLine();
            int count = 0;
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");    
                if (parts.length >= 9) {
                    list.add(new Reservation(
                        parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(),
                            parts[4].trim(),Integer.parseInt(parts[5].trim()), parts[6].trim(),
                            parts[7].trim(),parts[8].trim()));
                    count++;
                }
            }
            // ...existing code...
        }
        catch(IOException ex){
            ex.printStackTrace();
        }   
        return list;
    }
    
    public synchronized String add(String roomNum, String name, String inDate, String outDate, int guestNum, String phone, String createdAt){
        String resId = "R-" + (System.currentTimeMillis() % 10000); // 간단한 ID 생성
        String ReservationStatus= "Unpaid";
        boolean isNewFile = !new File(RES_FILE_PATH).exists();
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(RES_FILE_PATH, true))) {
            if (isNewFile) {
                bw.write("ResID,RoomNum,GuestName,CheckIn,CheckOut,Guests,Phone,ReservationStatus,CreatedAt");
                bw.newLine();
            } else {
                bw.newLine();
            }
            // 9개 필드 저장
            String line = String.format("%s,%s,%s,%s,%s,%d,%s,%s,%s", 
                    resId, roomNum, name, inDate, outDate, guestNum, phone, ReservationStatus, createdAt);
            bw.write(line);
            return resId;
        }
        catch (IOException ex){ 
            return null; 
        }
    }
    
    private boolean rewriteFile(List<Reservation> all) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(RES_FILE_PATH))) {
            bw.write("ResID,RoomNum,GuestName,CheckIn,CheckOut,Guests,Phone,ReservationStatus,CreatedAt");
            for (Reservation r : all) {
                bw.newLine();
                bw.write(r.toString());
            }
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }
    
    public synchronized boolean updateStatus(String resId, String reservationStatus) {
        List<Reservation> all = findAll();
        boolean found = false;
        for (Reservation r : all) {
            if (r.getReservationId().equals(resId)) {
                r.setReservationStatus(reservationStatus);
                found = true;
                break;
            }
        }
        if(!found)
            return false;
        return rewriteFile(all);
    }
    
    public synchronized boolean delete(String resId) {
        List<Reservation> all = findAll();
        if (all.removeIf(r -> r.getReservationId().equals(resId))) {
            return rewriteFile(all);
        }
        return false;
    }
}