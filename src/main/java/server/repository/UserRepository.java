package server.repository;
import server.model.User;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
/**
 *
 * @author user
 */
public class UserRepository {
    private static final String USER_FILE_PATH = "data/users.csv"; // CSV 파일 경로 (단일 경로)
    private static final int ID_INDEX = 0;
    private static final int PW_INDEX = 1;
    private static final int ROLE_INDEX = 2;
    private static final int PHONE_INDEX = 3;
    private static final int NAME_INDEX = 4;
    
    /**
     * 아이디로 사용자 한 명을 조회.
     * 헤더 한 줄을 건너뛰고 이후 행에서 매칭.
     * @param id 조회할 사용자 ID
     * @return User 또는 null
     */
    public synchronized User findByUsername(String id) {
        File f = new File(USER_FILE_PATH);
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine(); // 헤더 스킵
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");
                if(parts.length == 5 && parts[ID_INDEX].trim().equals(id)){
                    String phone = parts[PHONE_INDEX].trim();
                    String name = parts[NAME_INDEX].trim();
                    return new User(parts[ID_INDEX].trim(), name, parts[PW_INDEX].trim(), parts[ROLE_INDEX].trim(), phone);
                }
            }
        }
        catch(IOException ex){
            System.out.println("CVS 파일찾기 오류");
            ex.printStackTrace();
        }
        return null; //사용자를 찾기 못함
    } 

    /** 아이디 존재 여부 */
    public synchronized boolean existsByUsername(String id){
        return findByUsername(id) != null;
    }
    
    /** 모든 사용자 목록 조회 */
    public synchronized List<User> findAll(){
        List<User> userList = new ArrayList<>();
        File file = new File(USER_FILE_PATH);
        
        if(!file.exists()) return userList; // 빈 리스트 반환
        
        try(BufferedReader reader = new BufferedReader(new FileReader(file))){
            String line;
            reader.readLine();
            
            while((line = reader.readLine()) != null){
                String[] parts = line.split(",");
                if(parts.length == 5){
                    String phone = parts[PHONE_INDEX].trim();
                    String name = parts[NAME_INDEX].trim();
                    userList.add(new User(parts[ID_INDEX].trim(), name, parts[PW_INDEX].trim(), parts[ROLE_INDEX].trim(), phone));
                }
            }
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
        return userList;
    }
    
    /**
     * 사용자 추가 (중복 검사하지 않음 - 상위 서비스에서 수행)
     * 파일이 없거나 비어있으면 헤더 추가 후 행 append.
     */
    public synchronized boolean add(User user){
        File file = new File(USER_FILE_PATH);
        boolean needHeader = !file.exists() || file.length() == 0;
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))){
            if(needHeader){
                writer.write("ID,Password,Role,Phone,Name");
                writer.newLine();
                writer.flush(); // 헤더와 데이터 사이 줄바꿈 보장
            }
            // 파일이 비어있지 않더라도 마지막 줄이 개행으로 끝나지 않은 경우를 방지
            // (윈도우 메모장 등에서 파일이 잘못 저장된 경우)
            // 항상 줄의 시작에 데이터가 오도록 강제
            else {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                if (raf.length() > 0) {
                    raf.seek(raf.length() - 1);
                    int last = raf.read();
                    if (last != '\n' && last != '\r') {
                        writer.newLine();
                    }
                }
                raf.close();
            }
            String line = String.format("%s,%s,%s,%s,%s", user.getId(), user.getPassword(), user.getRole(), user.getPhone(), user.getName());
            writer.write(line);
            writer.newLine();
            System.out.println("[UserRepository] users.csv에 사용자 추가됨: " + line);
            return true;
        }
        catch(IOException ex){
            System.out.println("[UserRepository] users.csv 저장 오류: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }
    
    /** 아이디로 사용자 삭제 */
    public synchronized boolean delete(String id){
        List<User> allUsers = findAll();
        boolean removed = allUsers.removeIf(u -> u.getId().equals(id));
        if(!removed) return false;
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE_PATH))){
            writer.write("ID,Password,Role,Phone,Name");
            for(User u : allUsers){
                writer.newLine();
                String line = String.format("%s,%s,%s,%s,%s", u.getId(), u.getPassword(), u.getRole(), u.getPhone(), u.getName());
                writer.write(line);
            }
            System.out.println("[UserRepository] users.csv에서 사용자 삭제됨: " + id);
            return true;
        }
        catch(IOException ex){
            System.out.println("[UserRepository] users.csv 삭제 오류: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    /** 사용자 정보 수정 (비밀번호/권한/전화번호 변경) */
    public synchronized boolean update(User updated) {
        List<User> all = findAll();
        boolean found = false;
        for(User u : all) {
            if(u.getId().equals(updated.getId())) {
                u.setName(updated.getName());
                u.setPassword(updated.getPassword());
                u.setRole(updated.getRole());
                u.setPhone(updated.getPhone());
                found = true;
                break;
            }
        }
        if(!found) return false;
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE_PATH))) {
            writer.write("ID,Password,Role,Phone,Name");
            for(User u : all) {
                writer.newLine();
                String line = String.format("%s,%s,%s,%s,%s", u.getId(), u.getPassword(), u.getRole(), u.getPhone(), u.getName());
                writer.write(line);
            }
            System.out.println("[UserRepository] users.csv에서 사용자 수정됨: " + updated.getId());
            return true;
        } catch(IOException ex) {
            System.out.println("[UserRepository] users.csv 수정 오류: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    // saveUser 메서드 기능을 add에 통합 (중복 제거)

}


