package com.ruoyi.education.service.impl;

import com.ruoyi.education.domain.EduTerm;
import com.ruoyi.education.mapper.EduTermMapper;
import com.ruoyi.education.service.IEduTermService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EduTermServiceImpl implements IEduTermService {

    @Autowired
    private EduTermMapper termMapper;

    @Override
    public List<EduTerm> list(EduTerm query) {
        return termMapper.selectList(query);
    }

    @Override
    public EduTerm getById(Long id) {
        return termMapper.selectById(id);
    }

    @Override
    public Long create(EduTerm term) {
        termMapper.insert(term);
        return term.getId();
    }

    @Override
    public void update(EduTerm term) {
        termMapper.update(term);
    }
}
