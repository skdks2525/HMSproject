/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package cse.oop2.hms_server.src.main.server.net;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
/**
 *  HMS서버 메인 클래스
 * @author user
 */
public class ServerMain {

    private static final int PORT = 5000; // TCP서버 포트번호
    
    public static void main(String[] args) {
        System.out.println("HMS 서버 시작");
        
        try{
            ServerSocket serverSocket = new ServerSocket(PORT);
            while(true){
                Socket clientSocket = serverSocket.accept();
                System.out.println("클라이언트 접속");
                Thread t = new Thread(new ClientHandler(clientSocket));
            t.start();
            }
        }
        
        catch(IOException ex){
            ex.printStackTrace();
        }
    }
}
