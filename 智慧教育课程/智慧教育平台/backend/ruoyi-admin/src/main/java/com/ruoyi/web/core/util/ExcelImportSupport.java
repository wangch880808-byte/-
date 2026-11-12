package com.ruoyi.web.core.util;

import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;

public final class ExcelImportSupport
{
    private ExcelImportSupport() { }
    public static void checkFile(MultipartFile file)
    {
        if (file == null || file.isEmpty()) throw new ServiceException("请选择非空 Excel 文件", 400);
        String name = file.getOriginalFilename();
        name = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xls") && !name.endsWith(".xlsx")) throw new ServiceException("仅支持 .xls 或 .xlsx 文件", 400);
        if (file.getSize() > 10L * 1024 * 1024) throw new ServiceException("文件不能超过10MB", 400);
    }
}
