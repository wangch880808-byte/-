package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduTerm;
import java.util.List;

public interface EduTermMapper {
    List<EduTerm> selectList(EduTerm query);
    EduTerm selectById(Long id);
    int insert(EduTerm term);
    int update(EduTerm term);
}
