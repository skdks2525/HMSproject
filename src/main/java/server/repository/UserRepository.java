/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package server.repository;
import server.model.User;
import java.io.*;
import java.net.*;
/**
 * 사용자 CSV(users.csv) 파일에 접근하는 리포지토리.
 */
public class UserRepository {
    // user.csv 파일 경로 (혹시나 안되면 data/ 빼보셈)
    private static final String USER_FILE_PATH = "data/users.csv";
    private static final int ID_INDEX = 0;
    private static final int PW_INDEX = 1;
    private static final int ROLE_INDEX = 2;
    
    /**
     * 아이디로 사용자 한 명을 조회.
     * - 첫 줄 헤더는 한 줄 건너뜀
     * - 일치하는 아이디가 있으면 User 객체 반환, 없으면 null
     */
    public synchronized User findByUsername(String id) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(USER_FILE_PATH));
            String line;
            reader.readLine(); // 헤더 스킵
            
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");
                // id 일치하면 User객체 생성 후 반환
                if(parts.length == 3 && parts[ID_INDEX].equals(id)){
                    return new User(parts[ID_INDEX], parts[PW_INDEX], parts[ROLE_INDEX]);
                }
            }
        }
        catch(IOException ex){
            System.out.println("CVS 파일찾기 오류");
            ex.printStackTrace();
        }
        
        return null; //사용자를 찾지 못함
    }

    /** 아이디 중복 여부 확인 */
    public synchronized boolean existsByUsername(String id){
        return findByUsername(id) != null;
    }

    /** 사용자 저장: 파일이 없거나 비어있으면 헤더 작성 후 append
     *  헤더 처리 규칙:
     *  - 파일이 비어있는 경우(처음 생성된 상태) 한 번만 헤더를 기록한다.
     *  - 파일에 이미 내용이 있으면 헤더가 있다고 가정하고 데이터만 추가한다.
     *    (최소 변경 원칙. 내용은 있는데 헤더가 없는 비정상 파일을 보정하는 로직은 포함하지 않음)
     */
    public synchronized boolean saveUser(User user){
        File file = new File(USER_FILE_PATH);
        boolean needHeader = false;
        try {
            // 비어있는 파일이면 헤더를 한 번만 기록한다.
            // 비어있지 않으면 헤더가 이미 있다고 간주한다.
            if(file.length() == 0){
                needHeader = true; // 빈 파일이면 헤더 추가
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            if(needHeader){
                writer.write("ID,Password,Role");
                writer.newLine();
            }
            // CSV: id,pw,role
            writer.write(user.getId() + "," + user.getPassword() + "," + user.getRole());
            writer.newLine();
            writer.flush();
            writer.close();
            return true;
        }
        catch(IOException ex){
            System.out.println("CVS 저장 오류");
            ex.printStackTrace();
            return false;
        }
    }
}


