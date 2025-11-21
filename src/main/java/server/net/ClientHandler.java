/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.net;
import server.service.AuthService;
//import cse.oop2.hms_server.src.main.server.service.ReservationService;
import server.model.User;
import java.io.BufferedReader;
import java.io.*;
import java.net.*;

/**
 *  각 클라이언트 연결을 개별 스레드에서 처리하는 클래스 (Runnable 인터페이스 구현)
 * @author user
 */
public class ClientHandler implements Runnable {
    
    private final Socket clientSocket;
    private final AuthService authService;
    //private final Reservation reservationService;
    
    public ClientHandler(Socket socket){
        this.clientSocket = socket;
        
        this.authService = new AuthService(); // 사용할 서비스 객체 초기화
        //this.reservation = new ReservationService();
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
            String command = parts[0];
        
                
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
                    return "ERROR:Unknown command " + command;    
            }
        }

        catch(Exception ex){
                ex.printStackTrace();
                return "ERROR:Internal server error: " + ex.getMessage();
        }
    }
}
