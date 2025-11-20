/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cse.oop2.hms_server.src.main.server.repository;
import cse.oop2.hms_server.src.main.server.model.User;
import java.io.*;
import java.net.*;
/**
 *
 * @author user
 */
public class UserRepository {
    private static final String USER_FILE_PATH = "data/users.csv"; //user.csv 파일경로 (혹시나 안되면 data/ 빼보셈)
    private static final int ID_INDEX = 0;
    private static final int PW_INDEX = 1;
    private static final int ROLE_INDEX = 2;
    
    public synchronized User findByUsername(String id) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(USER_FILE_PATH));
            String line;
            reader.readLine();
            
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");
                
                 if(parts.length == 3 && parts[0].equals(id)){ //id 일치하면 User객체 생성 후 반환
                     return new User(parts[0], parts[1], parts[2]);
                 }
            }
        }
        catch(IOException ex){
            System.out.println("CVS 파일찾기 오류");
            ex.printStackTrace();
        }
        
        return null; //사용자를 찾기 못함
    }
}


