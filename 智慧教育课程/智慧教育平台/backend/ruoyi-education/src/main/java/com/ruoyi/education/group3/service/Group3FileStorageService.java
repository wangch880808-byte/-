package com.ruoyi.education.group3.service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;

@Service
public class Group3FileStorageService
{
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pptx", "pdf", "png", "jpg", "jpeg", "gif", "webp",
            "mp4", "webm", "mov", "docx", "txt", "md");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");
    private static final int MAX_PAGES = 150;

    static
    {
        System.setProperty("java.awt.headless", "true");
    }

    private final Path root;

    public Group3FileStorageService(@Value("${ruoyi.profile}") String profile)
    {
        this.root = Paths.get(profile, "education").toAbsolutePath().normalize();
    }

    public StoredUpload store(MultipartFile file, Long courseSpaceId)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要上传的文件", HttpStatus.BAD_REQUEST);
        }
        try
        {
            return write(file.getInputStream(), file.getOriginalFilename(),
                    file.getContentType(), courseSpaceId);
        }
        catch (IOException error)
        {
            throw new ServiceException("文件保存失败：" + error.getMessage(), HttpStatus.ERROR);
        }
    }

    /** 供压缩包导入使用：按文件名字节写入存储。 */
    public StoredUpload store(byte[] content, String originalName, Long courseSpaceId)
    {
        if (content == null || content.length == 0)
        {
            throw new ServiceException("文件内容为空", HttpStatus.BAD_REQUEST);
        }
        try (InputStream input = new java.io.ByteArrayInputStream(content))
        {
            return write(input, originalName, null, courseSpaceId);
        }
        catch (IOException error)
        {
            throw new ServiceException("文件保存失败：" + error.getMessage(), HttpStatus.ERROR);
        }
    }

    /** 判断文件名是否为平台支持的资料格式。 */
    public boolean supports(String fileName)
    {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(sanitizeName(fileName)));
    }

    private StoredUpload write(
            InputStream input, String submittedName, String submittedMime, Long courseSpaceId)
            throws IOException
    {
        String originalName = sanitizeName(submittedName);
        String extension = extensionOf(originalName);
        if (!SUPPORTED_EXTENSIONS.contains(extension))
        {
            throw new ServiceException("暂不支持该文件格式，请上传 PPTX、PDF、图片、视频、DOCX 或文本文件",
                    HttpStatus.UNSUPPORTED_TYPE);
        }

        String storageKey = "resources/" + courseSpaceId + "/" + UUID.randomUUID()
                + "/" + originalName;
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredUpload(storageKey, sha256(target), mimeType(submittedMime, extension),
                Files.size(target), originalName, extension);
    }

    public List<ConvertedPage> convert(String storageKey, String originalName)
    {
        Path source = resolve(storageKey);
        String extension = extensionOf(originalName);
        try
        {
            if ("pptx".equals(extension))
            {
                return convertPptx(source);
            }
            if ("pdf".equals(extension))
            {
                return convertPdf(source);
            }
            if (IMAGE_EXTENSIONS.contains(extension))
            {
                return List.of(convertImage(source));
            }
            if (VIDEO_EXTENSIONS.contains(extension))
            {
                return List.of();
            }
            if ("docx".equals(extension) || "txt".equals(extension) || "md".equals(extension))
            {
                return convertDocument(source, extension, originalName);
            }
        }
        catch (IOException | RuntimeException error)
        {
            throw new ServiceException("资料转换失败：" + error.getMessage(), HttpStatus.ERROR);
        }
        throw new ServiceException("暂不支持该文件的在线预览", HttpStatus.UNSUPPORTED_TYPE);
    }

    public Resource load(String storageKey)
    {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path))
        {
            throw new ServiceException("文件不存在或已被移除", HttpStatus.NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    public Path path(String storageKey)
    {
        return resolve(storageKey);
    }

    public String fileName(String storageKey)
    {
        return Paths.get(storageKey).getFileName().toString();
    }

    public String contentType(String storageKey)
    {
        String extension = extensionOf(storageKey);
        return switch (extension)
        {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mov" -> "video/quicktime";
            case "txt", "md" -> "text/plain;charset=UTF-8";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    public String mediaKind(String storageKey)
    {
        String extension = extensionOf(storageKey);
        if (IMAGE_EXTENSIONS.contains(extension))
        {
            return "IMAGE";
        }
        if (VIDEO_EXTENSIONS.contains(extension))
        {
            return "VIDEO";
        }
        if ("pdf".equals(extension) || "pptx".equals(extension))
        {
            return "SLIDES";
        }
        return "DOCUMENT";
    }

    private List<ConvertedPage> convertPptx(Path source) throws IOException
    {
        List<ConvertedPage> pages = new ArrayList<>();
        try (XMLSlideShow slideShow = new XMLSlideShow(Files.newInputStream(source)))
        {
            List<? extends Slide<?, ?>> slides = slideShow.getSlides();
            if (slides.size() > MAX_PAGES)
            {
                throw new ServiceException("课件页数超过 " + MAX_PAGES + " 页，请拆分后上传");
            }
            Dimension originalSize = slideShow.getPageSize();
            double scale = Math.min(1600d / originalSize.getWidth(), 900d / originalSize.getHeight());
            int width = Math.max(640, (int) Math.round(originalSize.getWidth() * scale));
            int height = Math.max(360, (int) Math.round(originalSize.getHeight() * scale));
            for (int index = 0; index < slides.size(); index++)
            {
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.scale(scale, scale);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                slides.get(index).draw(graphics);
                graphics.dispose();
                pages.add(writePreview(image, index + 1, width, height));
            }
        }
        return pages;
    }

    private List<ConvertedPage> convertPdf(Path source) throws IOException
    {
        List<ConvertedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(source.toFile()))
        {
            if (document.getNumberOfPages() > MAX_PAGES)
            {
                throw new ServiceException("PDF 页数超过 " + MAX_PAGES + " 页，请拆分后上传");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            for (int index = 0; index < document.getNumberOfPages(); index++)
            {
                BufferedImage rendered = renderer.renderImageWithDPI(index, 120, ImageType.RGB);
                BufferedImage image = scaleDown(rendered, 1600);
                pages.add(writePreview(image, index + 1, image.getWidth(), image.getHeight()));
            }
        }
        return pages;
    }

    private ConvertedPage convertImage(Path source) throws IOException
    {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null)
        {
            throw new IOException("无法读取图片内容");
        }
        return writePreview(scaleDown(image, 1600), 1, image.getWidth(), image.getHeight());
    }

    private List<ConvertedPage> convertDocument(Path source, String extension, String originalName)
            throws IOException
    {
        String text;
        if ("docx".equals(extension))
        {
            try (XWPFDocument document = new XWPFDocument(Files.newInputStream(source)))
            {
                StringBuilder builder = new StringBuilder();
                for (XWPFParagraph paragraph : document.getParagraphs())
                {
                    builder.append(paragraph.getText()).append('\n');
                }
                text = builder.toString();
            }
        }
        else
        {
            text = Files.readString(source, StandardCharsets.UTF_8);
        }

        List<String> lines = wrapText(text.isBlank() ? originalName : text, 62);
        List<ConvertedPage> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += 24)
        {
            List<String> pageLines = lines.subList(start, Math.min(start + 24, lines.size()));
            pages.add(writePreview(renderTextPage(originalName, pageLines), pages.size() + 1, 1280, 720));
        }
        return pages.isEmpty() ? List.of(writePreview(
                renderTextPage(originalName, List.of("空白文档")), 1, 1280, 720)) : pages;
    }

    private BufferedImage renderTextPage(String title, List<String> lines)
    {
        int width = 1280;
        int height = 720;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(31, 45, 61));
        graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        graphics.drawString(title, 64, 68);
        graphics.setColor(new Color(80, 90, 104));
        graphics.setFont(new Font("Microsoft YaHei", Font.PLAIN, 20));
        int y = 120;
        for (String line : lines)
        {
            graphics.drawString(line, 64, y);
            y += 23;
        }
        graphics.dispose();
        return image;
    }

    private List<String> wrapText(String text, int maxChars)
    {
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace("\r", "").split("\n"))
        {
            String remaining = rawLine;
            if (remaining.isEmpty())
            {
                lines.add("");
                continue;
            }
            while (remaining.length() > maxChars)
            {
                lines.add(remaining.substring(0, maxChars));
                remaining = remaining.substring(maxChars);
            }
            lines.add(remaining);
        }
        return lines;
    }

    private BufferedImage scaleDown(BufferedImage source, int maxWidth)
    {
        if (source.getWidth() <= maxWidth)
        {
            return source;
        }
        double scale = maxWidth / (double) source.getWidth();
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, maxWidth, height, null);
        graphics.dispose();
        return scaled;
    }

    private ConvertedPage writePreview(BufferedImage image, int pageNo, int width, int height) throws IOException
    {
        String previewKey = "previews/" + UUID.randomUUID() + "/page-" + pageNo + ".png";
        Path target = resolve(previewKey);
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "png", target.toFile());
        return new ConvertedPage(pageNo, previewKey, width, height);
    }

    private Path resolve(String key)
    {
        if (key == null || key.isBlank())
        {
            throw new ServiceException("文件键为空", HttpStatus.BAD_REQUEST);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root))
        {
            throw new ServiceException("非法文件路径", HttpStatus.FORBIDDEN);
        }
        return path;
    }

    private String sanitizeName(String fileName)
    {
        String name = fileName == null ? "upload" : Paths.get(fileName).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return name.isBlank() ? "upload" : name;
    }

    private String extensionOf(String name)
    {
        int index = name == null ? -1 : name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeType(String submitted, String extension)
    {
        if (submitted != null && !submitted.isBlank() && !"application/octet-stream".equals(submitted))
        {
            return submitted;
        }
        return contentType("file." + extension);
    }

    private String sha256(Path path) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest())
            {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        }
        catch (Exception error)
        {
            throw new IOException("无法计算文件摘要", error);
        }
    }

    public record StoredUpload(
            String storageKey,
            String sha256,
            String mimeType,
            long byteSize,
            String originalName,
            String extension)
    {
    }

    public record ConvertedPage(int pageNo, String previewStorageKey, int width, int height)
    {
    }
}
