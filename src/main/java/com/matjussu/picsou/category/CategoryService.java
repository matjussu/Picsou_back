package com.matjussu.picsou.category;

import com.matjussu.picsou.category.dto.CategoryResponse;
import com.matjussu.picsou.category.dto.CreateCategoryRequest;
import com.matjussu.picsou.category.dto.UpdateCategoryRequest;
import com.matjussu.picsou.transaction.TransactionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categories;
  private final CategoryMapper mapper;
  private final TransactionRepository transactions;

  public List<CategoryResponse> list(UUID userId) {
    return categories.findByUserIdIsNullOrUserId(userId).stream().map(mapper::toDto).toList();
  }

  public CategoryResponse create(UUID userId, CreateCategoryRequest req) {
    Category c =
        categories.save(
            Category.builder()
                .userId(userId)
                .name(req.name())
                .iconKey(req.iconKey())
                .colorKey(req.colorKey())
                .parentId(req.parentId())
                .isDefault(false)
                .build());
    return mapper.toDto(c);
  }

  public CategoryResponse update(UUID userId, UUID id, UpdateCategoryRequest req) {
    Category c = ownedCustom(userId, id);
    if (req.name() != null) {
      c.setName(req.name());
    }
    if (req.iconKey() != null) {
      c.setIconKey(req.iconKey());
    }
    if (req.colorKey() != null) {
      c.setColorKey(req.colorKey());
    }
    return mapper.toDto(categories.save(c));
  }

  public void delete(UUID userId, UUID id) {
    Category c =
        categories
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie inconnue"));
    if (c.isDefault()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Catégorie par défaut non supprimable");
    }
    if (c.getUserId() == null || !c.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie inconnue");
    }
    if (transactions.existsByUserIdAndCategoryId(userId, id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Catégorie référencée par des transactions");
    }
    categories.delete(c);
  }

  private Category ownedCustom(UUID userId, UUID id) {
    return categories
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie inconnue"));
  }
}
