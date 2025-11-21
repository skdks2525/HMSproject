/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.service;
import server.model.User;
import server.repository.UserRepository;

/**
 * 인증관련 로직 처리하는 클래스
 * @author user
 */
public class AuthService {
   private final UserRepository userRepository; // user리퍼지토리 에서 데이터 가져오기
   
   public AuthService(){
       this.userRepository = new UserRepository(); // AuthService 생성 시 유저 리퍼지토리 함께 생성
   }
   
   //이제부터 로그인 시도
   public User login(String id, String pw){
       User user = userRepository.findByUsername(id);
       
       // 사용자가 존재하지 않음
       if(user == null){
           System.out.println("사용자를 찾을 수 없음");
           return null;
       }
       // 비밀번호 일치
       if(user.getPassword().equals(pw)){
           System.out.println("로그인 성공");
           return user;
       }
       else{
           System.out.println("비밀번호 불일치");
           return null;
       }
   }
}
