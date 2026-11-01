package com.ruoyi.education.service;

import com.ruoyi.education.domain.EduTerm;
import java.util.List;

public interface IEduTermService {
    List<EduTerm> list(EduTerm query);
    EduTerm getById(Long id);
    Long create(EduTerm term);
    void update(EduTerm term);
}
