package com.example.purchase.master.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Item;
import com.example.purchase.master.repository.ItemRepository;
import com.example.purchase.master.web.ItemForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final com.example.purchase.inventory.service.InventoryService inventoryService;

    /** 빈 문자열("")은 "조건 없음"으로 바꾼다. 안 바꾸면 category = '' 조건이 걸려 아무것도 안 나온다 */
    public Page<Item> search(String keyword, String category, Pageable pageable) {
        return itemRepository.search(blankToNull(keyword), blankToNull(category), pageable);
    }

    public List<Item> selectableItems() {
        return itemRepository.findByUsedTrueOrderByCategoryCodeAscNameAsc();
    }

    public Item get(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    @Transactional
    @Audited(action = AuditAction.CREATE, target = "품목", id = "#result")
    public Long create(ItemForm form) {
        if (itemRepository.existsByCode(form.getCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_ITEM_CODE);
        }
        Item item = new Item(form.getCode(), form.getName(), form.getCategoryCode(), form.getUnitCode(), form.getStandardPrice());
        itemRepository.save(item);
        inventoryService.createFor(item); // 재고 행(0개)을 같이 만든다
        return item.getId();
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, target = "품목", id = "#id")
    public void update(Long id, ItemForm form) {
        get(id).update(form.getName(), form.getCategoryCode(), form.getUnitCode(), form.getStandardPrice(), form.isUsed());
    }

    static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
