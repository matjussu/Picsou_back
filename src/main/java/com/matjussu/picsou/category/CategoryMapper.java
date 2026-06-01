package com.matjussu.picsou.category;

import com.matjussu.picsou.category.dto.CategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

  // Lombok génère le getter isDefault() → propriété MapStruct "default" ; on la mappe explicitement
  // sur le composant record isDefault pour éviter un drop silencieux du flag.
  @Mapping(target = "isDefault", source = "default")
  CategoryResponse toDto(Category category);
}
