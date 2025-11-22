/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.service;
import server.model.User;
import server.repository.UserRepository;
import java.util.List;

/**
 * 인증관련 로직 처리하는 클래스
 * @author user
 */
public class AuthService {
   private final UserRepository userRepository; // user리퍼지토리 에서 데이터 가져오기
   
   public AuthService(){
       this.userRepository = new UserRepository(); // AuthService 생성 시 유저 리퍼지토리 함께 생성
   }
   
   /**
    * 일반 사용자 회원가입 (role=Customer)
    * @return 성공 시 User, 실패 시 null
    */
   public synchronized User registerUser(String id, String pw){
       if(id == null || id.isEmpty() || pw == null || pw.isEmpty()){
           System.out.println("아이디/비밀번호 누락");
           return null;
       }
       // 중복 아이디 직접 검사 (리포지토리 add는 중복 검사하지 않음)
       if(userRepository.existsByUsername(id)){
           System.out.println("이미 존재하는 아이디");
           return null;
       }
       User user = new User(id, pw, "Customer");
       if(userRepository.add(user)){
           System.out.println("회원가입 성공");
           return user;
       }
       System.out.println("파일 저장 중 오류");
       return null;
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
   public List<User> getAllUsers(){
       return userRepository.findAll();
   }
   public boolean addUser(String id, String pw, String role){
       if(userRepository.existsByUsername(id)){
           return false;
       }
       return userRepository.add(new User(id, pw, role));
   }
   public boolean deleteUser(String id){
           return userRepository.delete(id);    
   }
}
