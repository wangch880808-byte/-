package com.ruoyi.education.group3.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Requests;

/**
 * 课程资料与题库的批量导入导出（不依赖任何本地文件夹）。
 */
@Service
public class Group3TransferService
{
    private static final String[] QUESTION_HEADERS = {
            "题目名称", "题型", "满分", "题干", "选项", "正确答案", "解析", "评分标准", "知识点"
    };

    // 与 application.yml 的单文件上传上限 200MB 保持一致
    private static final int MAX_IMPORT_BYTES = 200 * 1024 * 1024;

    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final Group3FileStorageService storage;
    private final ObjectMapper objectMapper;

    public Group3TransferService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            Group3PrepareService prepareService,
            Group3FileStorageService storage,
            ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.prepareService = prepareService;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /* ---------- 题库 Excel ---------- */

    public byte[] exportQuestions(Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Map<String, Object>> questions = mapper.selectQuestions(bindingId, courseSpaceId);

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("题库");
            Row header = sheet.createRow(0);
            for (int index = 0; index < QUESTION_HEADERS.length; index++)
            {
                header.createCell(index).setCellValue(QUESTION_HEADERS[index]);
                sheet.setColumnWidth(index, (index == 3 || index == 4 ? 48 : 18) * 256);
            }
            int rowIndex = 1;
            for (Map<String, Object> question : questions)
            {
                Row row = sheet.createRow(rowIndex++);
                String kind = text(question.get("questionKind"));
                List<String> options = parseOptions(question.get("optionsJson"));
                row.createCell(0).setCellValue(text(question.get("title")));
                row.createCell(1).setCellValue(kindLabel(kind));
                row.createCell(2).setCellValue(decimalText(question.get("maxScore"), "10"));
                row.createCell(3).setCellValue(text(question.get("stem")));
                row.createCell(4).setCellValue(optionLines(options));
                row.createCell(5).setCellValue(answerText(question.get("answerJson")));
                row.createCell(6).setCellValue(text(question.get("explanation")));
                row.createCell(7).setCellValue("");
                row.createCell(8).setCellValue(text(question.get("knowledgePoints")));
            }
            workbook.write(out);
            return out.toByteArray();
        }
        catch (IOException error)
        {
            throw new ServiceException("题库导出失败：" + error.getMessage(), HttpStatus.ERROR);
        }
    }

    public List<Map<String, Object>> previewQuestionImport(Long bindingId, MultipartFile file)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要导入的 Excel 文件", HttpStatus.BAD_REQUEST);
        }
        Map<String, Long> pointIds = knowledgePointIds(courseSpaceId);
        List<Map<String, Object>> result = new ArrayList<>();

        try (InputStream input = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(input))
        {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = headerColumns(sheet.getRow(0));
            if (!columns.containsKey("题干"))
            {
                throw new ServiceException("表格格式不正确，请使用导出的题库模板", HttpStatus.BAD_REQUEST);
            }
            DataFormatter formatter = new DataFormatter();
            for (int index = 1; index <= sheet.getLastRowNum(); index++)
            {
                Row row = sheet.getRow(index);
                if (row == null)
                {
                    continue;
                }
                Map<String, Object> item = parseQuestionRow(row, formatter, columns, pointIds);
                if (item == null)
                {
                    continue;
                }
                item.put("rowNo", index + 1);
                result.add(item);
            }
        }
        catch (IOException error)
        {
            throw new ServiceException("表格读取失败：" + error.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return result;
    }

    public Map<String, Object> importQuestions(Long bindingId, List<Map<String, Object>> rows)
    {
        accessService.requireTeacherBinding(bindingId);
        int imported = 0;
        List<Map<String, Object>> failures = new ArrayList<>();
        if (rows != null)
        {
            for (int index = 0; index < rows.size(); index++)
            {
                Map<String, Object> row = rows.get(index);
                try
                {
                    Group3Requests.Question request = new Group3Requests.Question(
                            bindingId,
                            text(row.get("title")),
                            text(row.get("questionKind")),
                            text(row.get("stem")),
                            stringList(row.get("options")),
                            objectMapper.valueToTree(row.get("answer")),
                            text(row.get("explanation")),
                            text(row.get("scoringRubric")),
                            new BigDecimal(defaultText(row.get("maxScore"), "10")),
                            longList(row.get("knowledgePointIds")));
                    prepareService.createQuestion(request);
                    imported++;
                }
                catch (RuntimeException error)
                {
                    Map<String, Object> failure = new LinkedHashMap<>();
                    failure.put("rowNo", row.get("rowNo") == null ? index + 1 : row.get("rowNo"));
                    failure.put("title", text(row.get("title")));
                    failure.put("reason", error.getMessage() == null ? "导入失败" : error.getMessage());
                    failures.add(failure);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    /* ---------- 资料 ZIP ---------- */

    public byte[] exportResources(Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Map<String, Object>> resources = mapper.selectTeacherResources(bindingId, courseSpaceId);

        Set<String> usedNames = new HashSet<>();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8))
        {
            for (Map<String, Object> resource : resources)
            {
                String storageKey = text(resource.get("storageKey"));
                if (storageKey.isBlank())
                {
                    continue;
                }
                Path path = storage.path(storageKey);
                if (!Files.isRegularFile(path))
                {
                    continue;
                }
                String extension = extensionOf(storageKey);
                String title = sanitizeEntryName(text(resource.get("title")));
                if (title.isBlank())
                {
                    title = "资料";
                }
                String entryName = title + (extension.isBlank() ? "" : "." + extension);
                entryName = uniqueName(entryName, usedNames);
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        }
        catch (IOException error)
        {
            throw new ServiceException("资料导出失败：" + error.getMessage(), HttpStatus.ERROR);
        }
    }

    public Map<String, Object> importResources(Long bindingId, MultipartFile file)
    {
        accessService.requireTeacherBinding(bindingId);
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要导入的 ZIP 压缩包", HttpStatus.BAD_REQUEST);
        }
        int imported = 0;
        int skipped = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        try (InputStream input = file.getInputStream();
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if (entry.isDirectory() || name.isBlank())
                {
                    skipped++;
                    continue;
                }
                String baseName = Paths.get(name).getFileName().toString();
                if (name.contains("__MACOSX/") || baseName.startsWith("."))
                {
                    skipped++;
                    continue;
                }
                if (!storage.supports(baseName))
                {
                    skipped++;
                    failures.add(failure(baseName, "不支持的格式"));
                    continue;
                }
                byte[] content = zip.readNBytes(MAX_IMPORT_BYTES + 1);
                if (content.length == 0)
                {
                    skipped++;
                    continue;
                }
                if (content.length > MAX_IMPORT_BYTES)
                {
                    skipped++;
                    failures.add(failure(baseName, "文件超过 200MB"));
                    continue;
                }
                String title = baseName.replaceFirst("\\.[^.]+$", "");
                try
                {
                    resourceService.importFile(bindingId, title, baseName, content);
                    imported++;
                }
                catch (RuntimeException error)
                {
                    failures.add(failure(baseName, error.getMessage() == null ? "导入失败" : error.getMessage()));
                }
            }
        }
        catch (IOException error)
        {
            throw new ServiceException("压缩包读取失败：" + error.getMessage(), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    /* ---------- 解析 Excel 行 ---------- */

    private Map<String, Object> parseQuestionRow(
            Row row,
            DataFormatter formatter,
            Map<String, Integer> columns,
            Map<String, Long> pointIds)
    {
        String title = cell(row, formatter, columns, "题目名称", "题目");
        String stem = cell(row, formatter, columns, "题干");
        if (title.isBlank() && stem.isBlank())
        {
            return null;
        }
        String rawKind = cell(row, formatter, columns, "题型");
        String kind = parseKind(rawKind);
        String maxScoreText = cell(row, formatter, columns, "满分", "分值");
        List<String> options = parseOptionCell(cell(row, formatter, columns, "选项"));
        String answerText = cell(row, formatter, columns, "正确答案", "答案");
        String explanation = cell(row, formatter, columns, "解析");
        String rubric = cell(row, formatter, columns, "评分标准");
        String pointText = cell(row, formatter, columns, "知识点");

        List<String> errors = new ArrayList<>();
        if (title.isBlank())
        {
            errors.add("题目名称不能为空");
        }
        if (stem.isBlank())
        {
            errors.add("题干不能为空");
        }
        if (kind == null)
        {
            errors.add("题型无法识别：" + rawKind);
            kind = "SINGLE";
        }
        BigDecimal maxScore = new BigDecimal("10");
        if (!maxScoreText.isBlank())
        {
            try
            {
                maxScore = new BigDecimal(maxScoreText);
            }
            catch (NumberFormatException error)
            {
                errors.add("满分必须是数字：" + maxScoreText);
            }
        }
        if (("SINGLE".equals(kind) || "MULTIPLE".equals(kind)) && options.size() < 2)
        {
            errors.add("选择题至少需要两个选项");
        }

        JsonNode answer = null;
        try
        {
            answer = buildAnswer(kind, answerText, options, errors);
        }
        catch (RuntimeException error)
        {
            errors.add(error.getMessage() == null ? "答案格式不正确" : error.getMessage());
        }

        List<Long> linkedPointIds = new ArrayList<>();
        List<String> unknownPoints = new ArrayList<>();
        for (String name : splitList(pointText))
        {
            Long pointId = pointIds.get(name);
            if (pointId == null)
            {
                unknownPoints.add(name);
            }
            else if (!linkedPointIds.contains(pointId))
            {
                linkedPointIds.add(pointId);
            }
        }
        if (!unknownPoints.isEmpty())
        {
            errors.add("知识点不存在：" + String.join("、", unknownPoints));
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("questionKind", kind);
        item.put("questionKindLabel", kindLabel(kind));
        item.put("stem", stem);
        item.put("options", options);
        item.put("maxScore", maxScore);
        item.put("answer", answer);
        item.put("answerText", answerText);
        item.put("explanation", explanation);
        item.put("scoringRubric", rubric);
        item.put("knowledgePointIds", linkedPointIds);
        item.put("knowledgePoints", String.join("、", splitList(pointText)));
        item.put("errors", errors);
        item.put("valid", errors.isEmpty());
        return item;
    }

    private JsonNode buildAnswer(
            String kind, String raw, List<String> options, List<String> errors)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank())
        {
            errors.add("请填写正确答案");
            return null;
        }
        if ("SINGLE".equals(kind))
        {
            int index = tokenToIndex(value, options);
            if (index < 0)
            {
                errors.add("单选题答案无法匹配选项：" + value);
                return null;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("choiceIndex", index);
            return node;
        }
        if ("MULTIPLE".equals(kind))
        {
            List<Integer> indices = new ArrayList<>();
            for (String token : value.split("[,，、;；\\s]+"))
            {
                if (token.isBlank())
                {
                    continue;
                }
                int index = tokenToIndex(token, options);
                if (index < 0)
                {
                    errors.add("多选题答案无法匹配选项：" + token);
                    return null;
                }
                if (!indices.contains(index))
                {
                    indices.add(index);
                }
            }
            if (indices.isEmpty())
            {
                errors.add("请填写多选题答案");
                return null;
            }
            ObjectNode node = objectMapper.createObjectNode();
            ArrayNode array = node.putArray("choiceIndices");
            indices.forEach(array::add);
            return node;
        }
        if ("TRUE_FALSE".equals(kind))
        {
            String normalized = value.toLowerCase(Locale.ROOT);
            boolean correct;
            if (value.contains("正确") || value.contains("对") || value.equals("是")
                    || value.equals("√") || normalized.equals("true") || normalized.equals("t")
                    || normalized.equals("1"))
            {
                correct = true;
            }
            else if (value.contains("错误") || value.contains("错") || value.equals("否")
                    || value.equals("×") || normalized.equals("false") || normalized.equals("f")
                    || normalized.equals("0"))
            {
                correct = false;
            }
            else
            {
                errors.add("判断题答案请填写“正确”或“错误”");
                return null;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("correctBoolean", correct);
            return node;
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("referenceAnswer", value);
        return node;
    }

    private int tokenToIndex(String token, List<String> options)
    {
        String value = token == null ? "" : token.trim();
        if (value.isBlank())
        {
            return -1;
        }
        String stripped = value.replaceFirst("^[A-Za-z][.、)．:：]\\s*", "").trim();
        if (value.length() == 1)
        {
            char letter = Character.toUpperCase(value.charAt(0));
            if (letter >= 'A' && letter <= 'Z')
            {
                return letter - 'A';
            }
        }
        if (value.matches("\\d+"))
        {
            return Integer.parseInt(value) - 1;
        }
        for (int index = 0; index < options.size(); index++)
        {
            String option = options.get(index).trim();
            if (option.equals(value) || option.equals(stripped))
            {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Long> knowledgePointIds(Long courseSpaceId)
    {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> point : mapper.selectKnowledgePoints(courseSpaceId))
        {
            result.put(text(point.get("name")), Group3AccessService.asLong(point.get("knowledgePointId")));
        }
        return result;
    }

    private Map<String, Integer> headerColumns(Row header)
    {
        if (header == null)
        {
            return Map.of();
        }
        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.getLastCellNum(); index++)
        {
            String name = formatter.formatCellValue(header.getCell(index)).trim();
            if (!name.isEmpty())
            {
                columns.put(name, index);
            }
        }
        return columns;
    }

    private String cell(Row row, DataFormatter formatter, Map<String, Integer> columns, String... names)
    {
        for (String name : names)
        {
            Integer index = columns.get(name);
            if (index != null && row.getCell(index) != null)
            {
                return formatter.formatCellValue(row.getCell(index)).trim();
            }
        }
        return "";
    }

    private List<String> parseOptions(Object raw)
    {
        if (raw == null)
        {
            return List.of();
        }
        try
        {
            JsonNode node = raw instanceof JsonNode json ? json : objectMapper.readTree(raw.toString());
            List<String> result = new ArrayList<>();
            if (node != null && node.isArray())
            {
                node.forEach(item -> result.add(item.asText()));
            }
            return result;
        }
        catch (Exception error)
        {
            return List.of();
        }
    }

    private List<String> parseOptionCell(String raw)
    {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return result;
        }
        for (String line : raw.replace("\r", "").split("\n"))
        {
            String value = line.replaceFirst("^[A-Za-z][.、)．:：]\\s*", "").trim();
            if (!value.isBlank())
            {
                result.add(value);
            }
        }
        return result;
    }

    private String optionLines(List<String> options)
    {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < options.size(); index++)
        {
            if (index > 0)
            {
                builder.append('\n');
            }
            builder.append((char) ('A' + index)).append(". ").append(options.get(index));
        }
        return builder.toString();
    }

    private String answerText(Object answerJson)
    {
        JsonNode node = parseJson(answerJson);
        if (node == null || node.isNull())
        {
            return "";
        }
        if (node.has("choiceIndex"))
        {
            return String.valueOf((char) ('A' + node.path("choiceIndex").asInt()));
        }
        if (node.has("choiceIndices"))
        {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node.path("choiceIndices"))
            {
                if (builder.length() > 0)
                {
                    builder.append(',');
                }
                builder.append((char) ('A' + item.asInt()));
            }
            return builder.toString();
        }
        if (node.has("correctBoolean"))
        {
            return node.path("correctBoolean").asBoolean() ? "正确" : "错误";
        }
        return node.path("referenceAnswer").asText("");
    }

    private JsonNode parseJson(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof JsonNode node)
        {
            return node;
        }
        try
        {
            return objectMapper.readTree(value.toString());
        }
        catch (Exception error)
        {
            return objectMapper.getNodeFactory().textNode(value.toString());
        }
    }

    private String parseKind(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty())
        {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("SINGLE".equals(upper) || value.contains("单选"))
        {
            return "SINGLE";
        }
        if ("MULTIPLE".equals(upper) || value.contains("多选"))
        {
            return "MULTIPLE";
        }
        if ("TRUE_FALSE".equals(upper) || value.contains("判断"))
        {
            return "TRUE_FALSE";
        }
        if ("SHORT_TEXT".equals(upper) || value.contains("简答"))
        {
            return "SHORT_TEXT";
        }
        return null;
    }

    private String kindLabel(String kind)
    {
        return switch (kind == null ? "" : kind)
        {
            case "SINGLE" -> "单选题";
            case "MULTIPLE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "SHORT_TEXT" -> "简答题";
            default -> "";
        };
    }

    private List<String> splitList(String raw)
    {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return result;
        }
        for (String item : raw.split("[,，、;；\\s]+"))
        {
            String value = item.trim();
            if (!value.isBlank() && !result.contains(value))
            {
                result.add(value);
            }
        }
        return result;
    }

    private String uniqueName(String name, Set<String> used)
    {
        if (used.add(name))
        {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        int index = 2;
        String candidate;
        do
        {
            candidate = base + " (" + index++ + ")" + extension;
        }
        while (!used.add(candidate));
        return candidate;
    }

    private String sanitizeEntryName(String value)
    {
        String name = value == null ? "" : value;
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    }

    private String extensionOf(String fileName)
    {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String text(Object value)
    {
        return value == null ? "" : value.toString().trim();
    }

    private String defaultText(Object value, String fallback)
    {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private String decimalText(Object value, String fallback)
    {
        if (value instanceof BigDecimal decimal)
        {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return defaultText(value, fallback);
    }

    private List<String> stringList(Object value)
    {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list)
        {
            list.forEach(item -> result.add(item == null ? "" : item.toString()));
        }
        return result;
    }

    private List<Long> longList(Object value)
    {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list)
        {
            for (Object item : list)
            {
                if (item instanceof Number number)
                {
                    result.add(number.longValue());
                }
                else if (item != null && !item.toString().isBlank())
                {
                    result.add(Long.valueOf(item.toString()));
                }
            }
        }
        return result;
    }

    private Map<String, Object> failure(String name, String reason)
    {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("name", name);
        failure.put("reason", reason);
        return failure;
    }
}
