/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package service;

import model.Room;
import model.Reservation;
import model.Reservation.ReservationStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 호텔의 객실 및 예약 관리를 담당하는 서비스 클래스
 * SFR-201~205 (객실/예약 조회), SFR-401~404 (예약 관리)
 */
public class HotelService {
    // 실제 운영 시에는 이 데이터를 파일/DB에서 로드
    private final List<Room> rooms = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();

    public HotelService() {
        // 초기 객실 데이터 로드 (시뮬레이션)
        rooms.add(new Room("101", "Single", 1, 80000));
        rooms.add(new Room("102", "Single", 1, 80000));
        rooms.add(new Room("201", "Double", 2, 120000));
        rooms.add(new Room("202", "Double", 2, 120000));
        rooms.add(new Room("301", "Suite", 4, 300000));
    }

    // --- SFR-201: 전체 객실 정보 조회 ---
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms);
    }

    // --- SFR-202: 객실 번호로 객실 정보 조회 ---
    public Optional<Room> getRoomByNumber(String roomNumber) {
        return rooms.stream()
                .filter(r -> r.getRoomNumber().equals(roomNumber))
                .findFirst();
    }

    // --- SFR-203: 예약 ID로 예약 정보 조회 ---
    public Optional<Reservation> getReservationById(String reservationId) {
        return reservations.stream()
                .filter(r -> r.getReservationId().equals(reservationId))
                .findFirst();
    }

    // --- SFR-204: 투숙객 이름으로 예약 목록 조회 ---
    public List<Reservation> getReservationsByGuestName(String guestName) {
        return reservations.stream()
                .filter(r -> r.getGuestName().equalsIgnoreCase(guestName))
                .collect(Collectors.toList());
    }

    // --- SFR-205: 특정 기간의 객실 점유율 조회 (간단 구현) ---
    /**
     * 특정 기간에 사용 가능한 객실 목록을 반환
     * SFR-205의 확장, 예약 가능 여부를 확인하는 기능 제공
     */
    public List<Room> getAvailableRooms(LocalDate checkInDate, LocalDate checkOutDate) {
        // 체크아웃 날짜는 체크인 날짜보다 늦어야 함
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜보다 늦어야 합니다.");
        }

        return rooms.stream()
                .filter(room -> isRoomAvailable(room.getRoomNumber(), checkInDate, checkOutDate))
                .collect(Collectors.toList());
    }

    // 특정 객실의 특정 기간 예약 가능 여부 확인
    private boolean isRoomAvailable(String roomNumber, LocalDate checkInDate, LocalDate checkOutDate) {
        return reservations.stream()
                .filter(r -> r.getRoomNumber().equals(roomNumber))
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.CHECKED_IN)
                .noneMatch(r -> r.overlapsWith(checkInDate, checkOutDate));
    }

    // --- SFR-401: 예약 생성 기능 ---
    /**
     * 새로운 예약을 생성하고 저장
     * @return 생성된 예약 객체
     * @throws IllegalArgumentException 예약 불가능 시 예외 발생
     */
    public Reservation createReservation(String roomNumber, String guestName, LocalDate checkInDate, LocalDate checkOutDate) {
        Optional<Room> roomOpt = getRoomByNumber(roomNumber);
        if (roomOpt.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 객실 번호입니다: " + roomNumber);
        }

        if (!isRoomAvailable(roomNumber, checkInDate, checkOutDate)) {
            throw new IllegalArgumentException(String.format("객실 %s는 해당 기간 (%s ~ %s)에 이미 예약되어 있습니다.",
                    roomNumber, checkInDate, checkOutDate));
        }

        // 고유 예약 ID 생성
        String reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reservation newReservation = new Reservation(reservationId, roomNumber, guestName, checkInDate, checkOutDate);
        reservations.add(newReservation);
        return newReservation;
    }

    // --- SFR-402: 예약 수정 기능 (날짜 및 고객 이름 수정) ---
    /**
     * 기존 예약의 정보를 수정
     * @return 수정된 예약 객체
     * @throws IllegalArgumentException 예약 ID가 없거나 수정된 기간에 다른 예약과 충돌할 경우
     */
    public Reservation updateReservation(String reservationId, String newGuestName, LocalDate newCheckInDate, LocalDate newCheckOutDate) {
        Reservation reservation = getReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약 ID를 찾을 수 없습니다: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("취소된 예약은 수정할 수 없습니다.");
        }

        // 수정 시에도 예약 가능 여부 재확인 (기존 예약을 제외하고 확인)
        boolean isConflict = reservations.stream()
                .filter(r -> !r.getReservationId().equals(reservationId)) // 자신을 제외
                .filter(r -> r.getRoomNumber().equals(reservation.getRoomNumber()))
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.CHECKED_IN)
                .anyMatch(r -> r.overlapsWith(newCheckInDate, newCheckOutDate));

        if (isConflict) {
            throw new IllegalArgumentException("수정된 기간에 객실이 이미 예약되어 있어 수정할 수 없습니다.");
        }

        reservations.removeIf(r -> r.getReservationId().equals(reservationId));
        Reservation updatedReservation = new Reservation(reservationId, reservation.getRoomNumber(),
                newGuestName, newCheckInDate, newCheckOutDate);
        reservations.add(updatedReservation);

        return updatedReservation;
    }

    // --- SFR-403: 예약 취소 기능 ---
    /**
     * 예약 상태를 CANCELLED로 변경하여 예약을 취소
     * @return 취소된 예약 객체
     * @throws IllegalArgumentException 예약 ID를 찾을 수 없을 경우
     */
    public Reservation cancelReservation(String reservationId) {
        Reservation reservation = getReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약 ID를 찾을 수 없습니다: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }

        // 상태 변경 (SFR-404: 예약 상태 변경 관리의 일부)
        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservation;
    }

    // --- SFR-404: 예약 상태 변경 관리 (Check-in/Check-out) ---
    /**
     * 예약 상태를 CHECKED_IN 또는 CHECKED_OUT으로 변경
     * @return 상태가 변경된 예약 객체
     * @throws IllegalArgumentException 예약 ID를 찾을 수 없을 경우
     */
    public Reservation updateReservationStatus(String reservationId, ReservationStatus newStatus) {
        Reservation reservation = getReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약 ID를 찾을 수 없습니다: " + reservationId));

        if (newStatus == ReservationStatus.CONFIRMED || newStatus == ReservationStatus.CANCELLED) {
            throw new IllegalArgumentException("이 메서드는 체크인/체크아웃 상태 변경에만 사용해야 합니다.");
        }

        reservation.setStatus(newStatus);
        return reservation;
    }
}
