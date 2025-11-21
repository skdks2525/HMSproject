/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.model;

/** 사용자 정보 담아두는 클래스
 * users.scv 파일의 사용자 1명씩 담아 둘 객체
 * @author user
 */
public class User {
    private String id;
    private String password;
    private String role;
    
    public User(String id, String password, String role){
        this.id = id;
        this.password = password;
        this.role = role;
    }
    
    public String getId(){
        return id;
    }
    
    public String getPassword(){
        return password;
    }
    
    public String getRole(){
        return role;
    }
}
