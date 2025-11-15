/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utility;

import java.io.*;
import java.util.Optional;

/**
 * 객체 직렬화 및 역직렬화를 위한 유틸리티 클래스
 * TCP 통신 시 객체를 바이트 배열로 변환하는 데 사용
 */
public class SerializationUtil {

    /**
     * 객체를 직렬화하여 바이트 배열로 변환
     * @param obj 직렬화할 객체
     * @return 직렬화된 바이트 배열
     * @throws IOException 직렬화 실패 시
     */
    public static <T extends Serializable> byte[] serialize(T obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    /**
     * 바이트 배열을 역직렬화하여 객체로 변환
     * @param data 역직렬화할 바이트 배열
     * @param <T> 반환될 객체의 타입
     * @return 역직렬화된 객체를 포함하는 Optional
     */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> Optional<T> deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            // Unchecked cast 경고를 억제하고 안전하게 형 변환을 시도
            return Optional.of((T) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error during deserialization: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
