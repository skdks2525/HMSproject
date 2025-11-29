package server.service;

import server.model.Menu;
import server.repository.MenuRepository;

import java.util.List;
import java.util.Optional; // NullPointerException 방지 (null값을 참조해도 NPE가 발생하지 않도록)

public class MenuService {
    // MenuRepository를 주입받아 데이터에 접근
    private final MenuRepository menuRepository;
    
    public MenuService() {
        this.menuRepository = new MenuRepository(); // menuservice 생성과 동시에 리포지토리 객체 생성
    }
    
    /**
     * SFR-503 식음료 메뉴 등록
     * @param menuId 메뉴 ID
     * @param name 메뉴 이름
     * @param price 메뉴 가격
     * @param category 메뉴의 종류
     * @param isAvailable 판매 여부
     * @return 등록되었는가?
     */
    public synchronized boolean AddMenu(String menuId, String name, int price, String category, boolean isAvailable, int stock) {
        Menu newMenu = new Menu(menuId, name, price, category, isAvailable, stock);
        newMenu.setIsAvailable(isAvailable);
        newMenu.setStock(stock);
        return menuRepository.save(newMenu);
    }
    
    /**
     * SFR-503 식음료 메뉴 수정
     * @param menuId 메뉴 ID
     * @param name 메뉴 이름
     * @param price 메뉴 가격
     * @param category 메뉴 종류
     * @param isAvailable 판매 여부
     * @return 업데이트되었는가?
     */
    
    public synchronized boolean updateMenu(String menuId, String name, int price, String category, boolean isAvailable, int stock) {
        Optional<Menu> menuOptional = menuRepository.findById(menuId);
        if (menuOptional.isPresent()) {
            Menu updatingMenu = menuOptional.get();
            updatingMenu.setName(name);
            updatingMenu.setPrice(price);
            updatingMenu.setCategory(category);
            updatingMenu.setIsAvailable(isAvailable);
            // 판매중지로 바꾸면 재고는 0으로 자동 변경
            if (!isAvailable) {
                updatingMenu.setStock(0);
            } else {
                updatingMenu.setStock(stock);
            }
            menuRepository.update(updatingMenu);
            return true;
        }
        return false;
    }
    
    /**
     * 메뉴 삭제
     * @param menuId 삭제할 메뉴 ID
     * @return 삭제되었는가?
     */
    public synchronized boolean deleteMenu(String menuId) {
        // Menu Optional 리스트 중 삭제할 menuId 탐색
        Optional<Menu> menuToDelete = menuRepository.findById(menuId);
        
        if (menuToDelete.isPresent()) {
            // 리포지토리에서 delete 메서드 반환(호출)
            return menuRepository.delete(menuId);
        }
        return false; // 메뉴를 찾지 못함
    }
    
    /**
     * 메뉴 목록 조회
     * @return 모든 메뉴 List
     */
    
    public synchronized List<Menu> getAllMenus() {
        return menuRepository.findAll();
    }
}


