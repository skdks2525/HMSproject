package server.net;
import server.service.*;
//import cse.oop2.hms_server.src.main.server.service.ReservationService;
import server.model.*;
import java.io.*;
import java.net.*;

/**
 *  각 클라이언트 연결을 개별 스레드에서 처리하는 클래스 (Runnable 인터페이스 구현)
 * @author user
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final AuthService authService;
    private final HotelService hotelService;
    private final MenuService menuService;
    private final server.service.MenuOrderService menuOrderService;
    private final ReportService reportService;

    public ClientHandler(Socket socket){
        this.clientSocket = socket;
        this.authService = new AuthService();
        this.hotelService = new HotelService();
        this.menuService = new MenuService();
        this.menuOrderService = new server.service.MenuOrderService();
        this.reportService = new ReportService(hotelService.getReservationRepository(), hotelService.getRoomRepository());
    }
    
    @Override
    public void run(){
        String request;
        
        try{
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(),true);
            
            while((request = in.readLine()) != null){
                System.out.println("클라이언트 요청: " + request);
                
                //여기에 핵심코드 작성
                String respone = handleRequest(request); //위임
                out.println(respone); //응답
            }
        }
        
        catch(IOException ex){
            System.out.println("클라이언트 통신 오류");
            ex.printStackTrace();
        }

    }
    
    private String handleRequest(String request){
        try{
            String[] parts = request.split(":", 3);
            String command = parts[0].trim();
            
            switch(command){
                case "LOGIN":
                    if(parts.length == 3){
                        String username = parts[1];
                        String password = parts[2];
                        
                        User user = authService.login(username, password);
                        if(user != null){
                            return "LOGIN_SUCCESS:" + user.getRole(); //로그인 성공    
                        }
                        else{    
                            return "LOGIN_FAIL:Invalid credentials"; // 로그인 실패    
                        }    
                    }    
                    else{    
                        return "ERROR:Invalid LOGIN format"; // 형식 오류
                    }    
                case "CHECKIN":
                    
                default:    
                    System.out.println("❌ [오류] 알 수 없는 명령어: [" + command + "]");
                    return "ERROR:Unknown command " + command;  
                    
                case "GET_USERS":
                    //서비스에서 모든 유저 가져오기
                    java.util.List<User> users = authService.getAllUsers();
                    StringBuilder sb = new StringBuilder("USER_LIST:");
                    for (User u : users) {
                        sb.append(u.getId()).append(",").append(u.getPassword()).append(",").append(u.getRole()).append(",").append(u.getPhone()).append(",").append(u.getName()).append("/");
                    }
                    return sb.toString();
                                case "GET_PAST_OCCUPANCY": {
                                    // GET_PAST_OCCUPANCY:yyyy-MM-dd:yyyy-MM-dd
                                    if (parts.length == 3) {
                                        String start = parts[1];
                                        String end = parts[2];
                                        return reportService.handlePastOccupancyRequest(start, end);
                                    }
                                    return "PAST_OCCUPANCY:";
                                }
                                case "GET_CURRENT_OCCUPANCY": {
                                    return reportService.handleCurrentOccupancyRequest();
                                }
                                case "GET_FUTURE_OCCUPANCY": {
                                    if (parts.length == 3) {
                                        String start = parts[1];
                                        String end = parts[2];
                                        return reportService.handleFutureOccupancyRequest(start, end);
                                    }
                                    return "FUTURE_OCCUPANCY:";
                                }
                case "ADD_USER":
                    // 형식(필수): ADD_USER:id:name:pw:role:phone  => 총 6토큰, 모두 공백불가
                    {
                        String[] addParts = request.split(":");
                        if(addParts.length != 6) return "ADD_FAIL:Format"; 
                        String id = addParts[1];
                        String name = addParts[2];
                        String pw = addParts[3];
                        String role = addParts[4];
                        String phone = addParts[5];
                        if(blank(id) || blank(name) || blank(pw) || blank(role) || blank(phone)) return "ADD_FAIL:FieldRequired";
                        boolean ok = authService.addUser(id, name, pw, role, phone);
                        return ok ? "ADD_SUCCESS" : "ADD_FAIL:DuplicateOrError";
                    }
                
                case "DELETE_USER":
                    if(parts.length >= 2) {
                        boolean ok = authService.deleteUser(parts[1]);
                        return ok ? "DELETE_SUCCESS" : "DELETE_FAIL";
                    }
                    return "DELETE_FAIL:Format";
                case "MODIFY_USER":
                    {
                        String[] mParts = request.split(":");
                        if(mParts.length != 6) return "MODIFY_FAIL:Format";
                        if(blank(mParts[1]) || blank(mParts[2]) || blank(mParts[3]) || blank(mParts[4]) || blank(mParts[5])) return "MODIFY_FAIL:FieldRequired";
                        boolean ok = authService.modifyUser(mParts[1], mParts[2], mParts[3], mParts[4], mParts[5]);
                        return ok ? "MODIFY_SUCCESS" : "MODIFY_FAIL";
                    }
                case "UPDATE_PAYMENT":
                    String[] p = request.split(":");
                    if(p.length == 7){
                        boolean ok = hotelService.processPayment(
                        p[1],p[2],p[3],p[4],p[5],p[6]);
                        return ok ? "PAYMENT_SUCCESS" : "PAYMENT_FAIL";
                    }
                    else {
                        System.out.println("[Server] 결제 요청 포맷 오류. 받은 개수: " + p.length);
                        return "ERROR:Format Error (Expected 7 parts)";
                    }
                    
                case "GET_MENUS":
                    java.util.List<Menu> menus = menuService.getAllMenus();
                    StringBuilder sb2 = new StringBuilder("MENU_LIST:");
                    for (Menu m : menus) {
                        String isAvailableStr = String.valueOf(m.getIsAvailable());
                        sb2.append(m.getMenuId()).append(",")
                          .append(m.getName()).append(",")
                          .append(m.getPrice()).append(",")
                          .append(m.getCategory()).append(",")
                          .append(isAvailableStr).append(",")
                          .append(m.getStock()).append("/");
                    }
                    return sb2.toString();
                case "GET_ROOM_SALES":
                    // 형식: GET_ROOM_SALES:yyyy-MM-dd:yyyy-MM-dd
                    // 요청 형식 검사 후 HotelService에 집계를 위임합니다.
                    // 클라이언트는 시작/종료 날짜(yyyy-MM-dd)를 전달하고,
                    // 서버는 "ROOM_SALES:yyyy-MM-dd=amount,yyyy-MM-dd=amount,..." 형태의 한 줄 응답을 반환합니다.
                    // LinkedHashMap을 사용해 날짜 순서가 유지되므로 클라이언트가 순서대로 그래프를 그릴 수 있습니다.
                    if (parts.length == 3) {
                        String start = parts[1];
                        String end = parts[2];

                        // HotelService에서 날짜별 매출을 계산해서 맵으로 돌려받습니다.
                        java.util.Map<java.time.LocalDate, Integer> sales = hotelService.getRoomSalesByDateRange(start, end);

                        // 응답 문자열을 구성합니다. 각 항목은 date=value(날짜=매출)로 콤마로 구분됩니다.
                        //ex ROOM_SALES:2025-10-01=220000,2025-10-02=440000,...
                        StringBuilder salesSb = new StringBuilder("ROOM_SALES:");
                        boolean first = true;
                        for (java.util.Map.Entry<java.time.LocalDate, Integer> en : sales.entrySet()) {
                            if (!first) salesSb.append(",");
                            salesSb.append(en.getKey().toString()).append("=").append(en.getValue());
                            first = false;
                        }
                        return salesSb.toString();
                    }
                    return "ERROR:Format";
                    
                case "ADD_MENU":
                {
                    String[] menuParts = request.split(":");
                    // 형식 검사: 토큰이 7개인지 확인
                    if (menuParts.length != 7) return "ADD_FAIL:Format";
                    String menuId = menuParts[1];
                    String name = menuParts[2];
                    String priceStr = menuParts[3];
                    String category = menuParts[4];
                    String isavailableStr = menuParts[5];
                    String stockStr = menuParts[6];
                    if (blank(menuId) || blank(name) || blank(priceStr) || blank(category) || blank(isavailableStr) || blank(stockStr)) {
                        return "ADD_FAIL:FieldRequired";
                    }
                    int price;
                    int stock;
                    try {
                        price = Integer.parseInt(priceStr.trim());
                        stock = Integer.parseInt(stockStr.trim());
                    } catch (NumberFormatException e) {
                        return "ADD_FAIL:InvaildPriceFormat";
                    }
                    boolean isAvailable = Boolean.parseBoolean(isavailableStr.trim());
                    boolean ok = menuService.AddMenu(menuId, name, price, category, isAvailable, stock);
                    return ok ? "ADD_SUCCESS" : "ADD_FAIL:DuplicateIdOrError";
                }
                
                case "DELETE_MENU":
                    // 형식: DELETE_MENU:menuId (총 2개 토큰)
                {
                    if (parts.length != 2) return "DELETE_FAIL:Format";
                    String menuId = parts[1];
                    
                    if (blank(menuId)) {
                        return "DELETE_FAIL:FieldRequired";
                    }
                    
                    // Service 호출
                    boolean ok = menuService.deleteMenu(menuId);
                    
                    // 결과 반환
                    return ok ? "DELETE_SUCCESS" : "DELETE_FAIL:NotFound";
                }
                
                case "UPDATE_MENU":
                {
                    String[] menuParts = request.split(":");
                    // 형식 검사: 토큰이 7개인지 확인
                    if (menuParts.length != 7) return "UPDATE_FAIL:Format";
                    String menuId = menuParts[1];
                    String name = menuParts[2];
                    String priceStr = menuParts[3];
                    String category = menuParts[4];
                    String isavailableStr = menuParts[5];
                    String stockStr = menuParts[6];
                    if (blank(menuId) || blank(name) || blank(priceStr) || blank(category) || blank(isavailableStr) || blank(stockStr)) {
                        return "UPDATE_FAIL:FieldRequired";
                    }
                    int price;
                    int stock;
                    try {
                        price = Integer.parseInt(priceStr.trim());
                        stock = Integer.parseInt(stockStr.trim());
                    } catch (NumberFormatException e) {
                        return "UPDATE_FAIL:InvaildPriceFormat";
                    }
                    boolean isAvailable = Boolean.parseBoolean(isavailableStr.trim());
                    boolean ok = menuService.updateMenu(menuId, name, price, category, isAvailable, stock);
                    return ok ? "UPDATE_SUCCESS" : "UPDATE_FAIL:NotFound";
                }
                
                            case "ORDER_MENU": {
                                // 프로토콜: ORDER_MENU:GuestName:TotalPrice:Payment:FoodName1|FoodName2|...
                                String[] orderParts = request.split(":", 5);
                                if (orderParts.length != 5) return "ORDER_FAIL:Format";
                                String guestName = orderParts[1];
                                int totalPrice;
                                try {
                                    totalPrice = Integer.parseInt(orderParts[2]);
                                } catch (NumberFormatException e) {
                                    return "ORDER_FAIL:PriceFormat";
                                }
                                String payment = orderParts[3];
                                String foodNamesStr = orderParts[4];
                                String[] foodNamesArr = foodNamesStr.split("\\|");
                                java.util.List<String> foodNames = java.util.Arrays.asList(foodNamesArr);
                                // 주문 ID 생성
                                String saleId = "S-" + (System.currentTimeMillis() % 1000000);
                                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                                // 주문 저장
                                server.model.MenuOrder order = new server.model.MenuOrder(saleId, guestName, now, totalPrice, payment, foodNames);
                                menuOrderService.saveOrder(order);
                                // 재고 차감 및 판매중지 처리
                                boolean allOk = true;
                                for (String food : foodNames) {
                                    java.util.Optional<server.model.Menu> menuOpt = menuService.getAllMenus().stream().filter(m -> m.getName().equals(food)).findFirst();
                                    if (menuOpt.isPresent()) {
                                        server.model.Menu menu = menuOpt.get();
                                        int stock = menu.getStock();
                                        if (stock > 0) {
                                            menu.setStock(stock - 1);
                                            if (menu.getStock() == 0) {
                                                menu.setIsAvailable(false);
                                            }
                                            menuService.updateMenu(menu.getMenuId(), menu.getName(), menu.getPrice(), menu.getCategory(), menu.getIsAvailable(), menu.getStock());
                                        } else {
                                            allOk = false;
                                        }
                                    } else {
                                        allOk = false;
                                    }
                                }
                                return allOk ? "ORDER_SUCCESS" : "ORDER_PARTIAL_FAIL:재고부족";
                            }
                case "GET_ALL_ROOMS":
                    StringBuilder roomSb = new StringBuilder("ROOM_LIST:");
                    for(Room r : hotelService.getAllRooms()){
                        roomSb.append(r.toString()).append("/");
                    }
                    return roomSb.toString();
                    
                case "GET_ROOM":
                    if (parts.length == 2) {
                        Room r = hotelService.getRoom(parts[1]);
                        return (r != null) ? "ROOM_INFO:" + r.toString() : "ERROR:Not Found";
                    }
                    return "ERROR:Format";
                        
                case "GET_RES_BY_NAME":
                    if (parts.length == 2) {
                        StringBuilder resSb = new StringBuilder("RES_LIST:");
                        for (Reservation r : hotelService.getReservationsByName(parts[1])) {
                            resSb.append(r.toString()).append("/");           
                        }
                        return resSb.toString();
                    }    
                    return "ERROR:Format";
                        
                case "GET_AVAILABLE_ROOMS":
                    // 날짜 로직 생략, 전체 방 목록 반환 (추후 구현)
                    StringBuilder availSb = new StringBuilder("ROOM_LIST:");
                    for (Room r : hotelService.getAllRooms()) {
                        availSb.append(r.toString()).append("/");
                    }
                    return availSb.toString();
                    
                case "CHECK_AVAILABILITY":
                    String[] checkParts = request.split(":");
                    if (checkParts.length == 3) {
                        String types = hotelService.getAvailableRoomTypes(checkParts[1], checkParts[2]);
                        return "AVAILABLE_TYPES:" + types;
                    }
                    return "ERROR:Format";
                    
               case "ADD_RESERVATION":
                    // ADD_RESERVATION:타입:이름:입실:퇴실:인원:폰
                    String[] resParts = request.split(":");
                    
                    if (resParts.length == 7) { 
                        // createReservationByType 호출 (자동 배정 로직)
                        String assignedRoom = hotelService.createReservationByType(
                            resParts[1], // Type
                            resParts[2], // Name
                            resParts[3], // In
                            resParts[4], // Out
                            Integer.parseInt(resParts[5]), // GuestNum
                            resParts[6]  // Phone
                        );
                        
                        if (assignedRoom != null) {
                            return "RESERVE_SUCCESS:" + assignedRoom;
                        } else {
                            return "RESERVE_FAIL:No Room Available";
                        }
                    }
                    return "ERROR:Format (Expected 7 parts)";
                    
                case "DELETE_RESERVATION":
                    if (parts.length == 2) {
                        boolean ok = hotelService.cancelReservation(parts[1]);
                        return ok ? "DELETE_SUCCESS" : "DELETE_FAIL";
                    }
                    return "ERROR:Format";
            }
        }

        catch(Exception ex){
                ex.printStackTrace();
                return "ERROR:Internal server error: " + ex.getMessage();
        }
    }

    // 공백/널 체크 유틸
    private boolean blank(String s){
        return s == null || s.trim().isEmpty();
    }
}
