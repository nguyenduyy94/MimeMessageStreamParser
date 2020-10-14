package com.nguyenduyy;

import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import org.jsoup.nodes.Element;
import sun.jvm.hotspot.debugger.Address;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public abstract class MimeMessageStreamParser {
    private String emailName;

    boolean parsing = false;
    Part header = new Part("main");
    boolean body = false;
    private final int CHUNK_SIZE = 8 * 256 * 1024; //256KB * 8 = 2MB
    boolean debug = false;
    OnByteHandlder onReceiveBytes = null;
    InlineImageHandler inlineImageHandler = null;
    Map<String, String> inlineImagePathMap;
    String emptySubject = "(Empty subject)";

    Logger logger = Logger.getLogger(MimeMessageStreamParser.class.getSimpleName());

    QuotedPrintableCodec quotedPrintableCodec = new QuotedPrintableCodec();

    /**
     * Constructor
     * @param emailName : Each MimeMessage eventually will be stored somewhere to be retrieved later. This name is to identify a MimeMessage in its storage.
     */
    public MimeMessageStreamParser(String emailName) {
        super();
        assert emailName != null;
        this.emailName = emailName;
    }

    public InlineImageHandler getInlineImageHandler() {
        return inlineImageHandler;
    }

    public void setInlineImageHandler(InlineImageHandler inlineImageHandler) {
        this.inlineImageHandler = inlineImageHandler;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getEmailName() {
        return emailName;
    }

    public String getEmptySubject() {
        return emptySubject;
    }

    public void setEmptySubject(String emptySubject) {
        this.emptySubject = emptySubject;
    }


    /**
     *  Define how to store current MimeMessage (as file or upload to a Storage, etc) for later retrieving
     * @param in : stream of MimeMessage
     */
    public abstract void store(InputStream in);

    public abstract InputStream retrieve();

    public void parse() throws Exception {
        InputStream inputStream = retrieve();
        extract(inputStream);
    }

    public void extract(InputStream in) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
        String line;
        PartBodyProcessor partbodyProcessor = new PartBodyProcessor();
        while ((line = bufferedReader.readLine()) != null) {
            if (debug) {
                logger.info("Process " + line);
            }
            if (!body) {
                if (line.startsWith("MIME-Version")) {
                    parsing = true;
                    System.out.println("Begin MIME ");
                    continue;
                }
                if (line.startsWith("From")) {
                    //get From
                    header.setFrom(getValue(line));
                    System.out.println("From " + header.getFrom());
                    header.setCurrentHeader(MimeHeader.FROM);
                    continue;
                }
                if (line.startsWith("To")) {
                    //Get To
                    header.setTo(getValue(line));
                    System.out.println("To " + header.getTo());
                    header.setCurrentHeader(MimeHeader.TO);

                    continue;
                }
                if (line.startsWith("Subject")) {
                    //Get subject
                    header.setSubject(getValue(line));
                    System.out.println("Subject " + header.getSubject());
                    header.setCurrentHeader(MimeHeader.SUBJECT);
                    continue;
                }
                if (line.startsWith("Content-Type")) {
                    //Get content type, is multipart or not
                    String value  = getValue(line);
                    System.out.println("Content Type " + value);
                    header.setContentType(value);
                    header.setCurrentHeader(MimeHeader.CONTENT_TYPE);
                    continue;
                }
                if (line.startsWith("\t")) {
                    header.appendHeader(line.replace("\t", ""));
                    continue;
                }
                if (line.isEmpty()) {
                    System.out.println("Begin MIME body");
                    body = true;
                    partbodyProcessor.extract(header, bufferedReader);
                    continue;
                }
            }
        }
        parsing = false;
        bufferedReader.close();
        System.out.println("Parsing email " + emailName + " done");
    }

    private String getValue(String line) {
        String[] parts = line.split(":");
        return parts.length > 1 ? parts[1] : emptySubject;
    }

    public String getMessages() throws Exception {
        Document document = Jsoup.parse("<div id=\"main\"></div>");
        Element root = document.getElementById("main");
        for (Part part : header.subparts) {
            Element element = part.getDisplayMessage();
            if (element != null) {
                root.appendChild(element);
            }
        }
        replaceInlineImage(document);
        return document.body().html();
    }


    public void replaceInlineImage(Document document) throws Exception {
        Elements elements = document.getElementsByTag("img");
        for (Element element : elements) {
            String src = element.attr("src");
            String id = src.replace("cid:", "").trim();
            if (inlineImagePathMap != null) {
                String uploadedFilePath = inlineImagePathMap.get(id);
                if (uploadedFilePath != null) {
                    element.attr("src", uploadedFilePath);
                }
            }
        }

    }

    public void setOnReceiveBytes(OnByteHandlder onReceiveBytes) {
        this.onReceiveBytes = onReceiveBytes;
    }


    public Part getHeader() {
        return header;
    }

    public String[] getHeader(String name) throws MessagingException {
        return null;
    }

    public Address[] getFrom() throws MessagingException {
        return null;
    }

    public Address getSender() throws MessagingException {
        return null;
    }

    public Address[] getReplyTo() throws MessagingException {
        return null;
    }

    public String getSubject() throws MessagingException {
        return null;
    }

    public Map<String, Part> getAttachments() {
        Map<String, Part> attachments = new HashMap<>();
        for (Part part: header.getSubparts()) {
            if (part.hasAttachment()) {
                attachments.put(part.getFileName(), part);
            }
        }
        return attachments;
    }

    public interface OnByteHandlder {
        void execute(byte[] data, int length, Part currentPart) throws Exception;
    }

    public interface InlineImageHandler {
        String execute(String fileName, byte[] image, boolean last) throws Exception;
    }


    public class Part {
        private String contentType;
        private String contentDiposition;
        private String contentTransferEncode;
        private String partID;
        private String contentID;
        private String attachmentID;
        private String uploadedAttachmentURL;
        private Integer attachmentSize = 0;
        private String fileName;
        private MimeHeader currentHeader;
        private String message;
        private String boundary;
        private String from, to, subject;

        public Part(String partID) {
            this.partID = partID;
        }

        List<Part> subparts = new ArrayList<>();

        public String getContentType() {
            return contentType;
        }

        public String getDataTypeFromContentType() {
            Matcher matcher = Pattern.compile("(Content-Type: image/)(.*);(.*)").matcher("Content-Type: image/gif; name=\"image009.gif\"");
            if (matcher.find()) {
                if (matcher.groupCount() > 2) {
                    return matcher.group(2);
                }
            }
            return "";
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getContentID() {
            return contentID;
        }

        public void setContentID(String contentID) {
            this.contentID = contentID;
        }

        public String getContentDiposition() {
            return contentDiposition;
        }

        public void setContentDiposition(String contentDiposition) {
            this.contentDiposition = contentDiposition;
        }

        public String getContentTransferEncode() {
            return contentTransferEncode;
        }

        public void setContentTransferEncode(String contentTransferEncode) {
            this.contentTransferEncode = contentTransferEncode;
        }

        public String getPartID() {
            return partID;
        }

        public void setPartID(String partID) {
            this.partID = partID;
        }

        public String getAttachmentID() {
            return attachmentID;
        }

        public boolean hasAttachment() {
            return getContentDiposition() != null && getContentDiposition().contains("attachment");
        }

        public boolean isInlineImage() {
            return getContentDiposition() != null && getContentDiposition().contains("inline");
        }

        public void setAttachmentID(String attachmentID) {
            this.attachmentID = attachmentID;
        }

        public String getUploadedAttachmentURL() {
            return uploadedAttachmentURL;
        }

        public void setUploadedAttachmentURL(String uploadedAttachmentURL) {
            this.uploadedAttachmentURL = uploadedAttachmentURL;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public MimeHeader getCurrentHeader() {
            return currentHeader;
        }

        public void setCurrentHeader(MimeHeader currentHeader) {
            this.currentHeader = currentHeader;
        }

        public boolean isHTMLPart() {
            return getContentType().contains("text/html");
        }

        public boolean isTextPlainPart() {
            return getContentType() != null && getContentType().contains("text/plain");
        }

        public void appendMessage(String s) throws DecoderException {
            appendMessage(s, true);
        }

        public void appendMessage(String s, boolean autoDecode) throws DecoderException {
            if (message == null) {
                message = "";
            }
            if (autoDecode && getContentTransferEncode() != null && s != null && !s.isEmpty()) {
                if (getContentTransferEncode().contains("base64")) {
                    message += new String(Base64.getDecoder().decode(s));
                } else if (getContentTransferEncode().contains("quoted-printable")) {
                    try {
                        //dont know why the library does not detect soft break character =.=
                        if (s.endsWith("=")) {
                            s = s.substring(0, s.length() - 1);
                        }
                        message += quotedPrintableCodec.decode(s, "UTF-8");
                    } catch (Exception e) {
                        logger.warning(e.getMessage() + ":" + s);
                    }
                }
            } else {
                message += s;
            }
        }

        public boolean isMultiPart() {
            return getContentType() != null && getContentType().contains("multipart");
        }

        private void processContentTypeHeader() {
            if (getContentType().contains("multipart")) {
                Pattern pattern = Pattern.compile("boundary=\"([^\"]*)\"");
                Matcher matcher = pattern.matcher(getContentType());
                if (matcher.find()) {
                    boundary = matcher.group(1);
                } else {
                    throw new Error("Can not extract boundary from " + getContentType());
                }
                System.out.println("Boundary " + boundary);
            }
        }

        public String getMessage() {
            return message;
        }

        public String getBoundary() {
            return boundary;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public void appendHeader(String value) {
            if (currentHeader != null) {
                if (currentHeader == MimeHeader.CONTENT_TYPE) {
                    contentType += value;
                    System.out.println("Update content type " + contentType);
                } else if (currentHeader == MimeHeader.CONTENT_DISPOSITION) {
                    contentDiposition += value;
                    System.out.println("Update content disposition " + contentDiposition);
                } else if (currentHeader == MimeHeader.FROM) {
                    from += value;
                    System.out.println("Update From " + from);
                } else if (currentHeader == MimeHeader.TO) {
                    to += value;
                    System.out.println("Update To " + to);
                } else if (currentHeader == MimeHeader.CONTENT_TRANSFER_ENCODING) {
                    contentTransferEncode += value;
                    System.out.println("Update contentTransferEncode " + to);
                } else if (currentHeader == MimeHeader.CONTENT_ID) {
                    contentID += value;
                    System.out.println("Update contentID " + to);

                }
            }

        }

        public List<Part> getSubparts() {
            return subparts;
        }

        public void setSubparts(List<Part> subparts) {
            this.subparts = subparts;
        }

        public Integer getAttachmentSize() {
            return attachmentSize;
        }

        public void setAttachmentSize(Integer attachmentSize) {
            this.attachmentSize = attachmentSize;
        }

        private String getFileNameFromContentDisposition() {
            String value = getContentDiposition();
            if (value.contains("attachment") || value.contains("inline")) {
                Pattern pattern = Pattern.compile("name=\"([^\"]*)\"");
                Matcher matcher = pattern.matcher(value);
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    throw new Error("Can not extract file name from " + value);
                }
            } else {
                return null;
            }
        }

        public Element getDisplayMessage() {
            if (this.isMultiPart()) {
                org.jsoup.nodes.Document document = Jsoup.parse("<div id=\""+ partID +"\"></div>");
                Element root = document.getElementById(partID);
                for (Part part : subparts) {
                    Element e = part.getDisplayMessage();
                    if (e != null) {
                        root.appendChild(e);
                    }
                }
                return document.body().getElementById(partID);
            } else {
                if (this.isHTMLPart()) {
                    Document document = Jsoup.parse("<div id=\"" + partID +"\">" + this.message + "</div>");
                    return document.getElementById(partID);
                } else if (this.hasAttachment()) {
                    return null;
                }
            }
            return null;
        }

        public void getInlineImageMap(Map<String, Part> result) {
            if (isInlineImage()) {
                result.put("cid:" + this.getContentID().trim()
                                .replace("<","").replace(">", "")
                        , this);
            } else {
                for (Part subPart : getSubparts()) {
                    subPart.getInlineImageMap(result);
                }
            }
        }
    }

    enum MimeHeader {
        CONTENT_TYPE {
            @Override
            public String toString() {
                return "Content-Type";
            }
        },
        CONTENT_DISPOSITION {
            @Override
            public String toString() {
                return "Content-Disposition";
            }
        },
        SUBJECT {
            @Override
            public String toString() {
                return "Subject";
            }
        },
        TO {
            @Override
            public String toString() {
                return "To";
            }
        },
        FROM {
            @Override
            public String toString() {
                return "From";
            }
        },
        CONTENT_TRANSFER_ENCODING {
            @Override
            public String toString() {
                return "Content-Transfer-Encoding";
            }
        },
        CONTENT_ID {
            @Override
            public String toString() {
                return "Content-ID";
            }
        }

    }

    class PartBodyProcessor {
        boolean partBody = false;
        ByteBuffer byteBuffer = ByteBuffer.allocate(CHUNK_SIZE);
        private int currentSize;

        PartBodyProcessor() {
        }

        private void flushByteBuffer(Part part, boolean last) throws Exception {
            if (part.hasAttachment()) {
                if (onReceiveBytes != null) {
                    byte[] arr = byteBuffer.array();
                    if (arr.length > 0) {
                        onReceiveBytes.execute(arr, currentSize, part);
                    }
                }

            } else if (part.isInlineImage()){
                if (inlineImageHandler != null) {
                    byte[] arr = byteBuffer.array();
                    String path = inlineImageHandler.execute(part.getFileNameFromContentDisposition(), arr, last);
                    if (inlineImagePathMap == null) inlineImagePathMap = new HashMap<>();
                    String id = part.getContentID().replace("<", "").replace(">", "").trim();
                    inlineImagePathMap.put(id, path);
                }
            }
            byteBuffer.clear();
            currentSize = 0;
        }

        public void extract(Part parentPart, BufferedReader bufferedReader) throws Exception {
            String line = null;
            parentPart.processContentTypeHeader();
            String boundary = parentPart.getBoundary();

            if (boundary != null) { //multipart part
                while ((line = bufferedReader.readLine()) != null) {
                    if (debug) {
                        logger.info("Process line: " + line);
                    }

                    if (line.startsWith("--" + boundary)) {
                        //start a part

                        //ensure flag last=true always goes
                        if (parentPart.getSubparts().size() > 0) {
                            this.flushByteBuffer(parentPart.getSubparts().get(parentPart.getSubparts().size() - 1), true);
                        }

                        this.partBody = false;

                        if (line.equals("--" + boundary + "--")) { //end of multipart
                            break;
                        }

                        String subPartID = parentPart.getPartID() + "-" + parentPart.getSubparts().size();
                        parentPart.getSubparts().add(new Part(subPartID));
                        System.out.println("Found a Part inside " + parentPart.getPartID());
                        continue;
                    }
                    if (parentPart.getSubparts().size() > 0) {
                        Part currentPart = parentPart.getSubparts().get(parentPart.getSubparts().size() - 1);
                        if (line.startsWith("Content-Type")) {
                            //determine attachment or text, contain another multipart or not
                            currentPart.setContentType(getValue(line));
                            System.out.println("-- Part content type " + currentPart.getContentType());
                            currentPart.setCurrentHeader(MimeHeader.CONTENT_TYPE);
                            continue;
                        }
                        if (line.startsWith("Content-Disposition")) {
                            //get filename
                            currentPart.setContentDiposition(getValue(line));
                            System.out.println("-- Part content disposition " + currentPart.getContentDiposition());
                            currentPart.setCurrentHeader(MimeHeader.CONTENT_DISPOSITION);
                            continue;
                        }
                        if (line.startsWith("Content-Transfer-Encoding")) {
                            //determine decoding algorithm and upload
                            currentPart.setContentTransferEncode(line);
                            System.out.println("-- Part content encode " + currentPart.getContentTransferEncode());
                            currentPart.setCurrentHeader(MimeHeader.CONTENT_TRANSFER_ENCODING);
                            continue;

                        }

                        if (line.startsWith("X-Attachment-Id")) {
                            currentPart.setAttachmentID(getValue(line));
                            System.out.println("-- Part attachment id " + currentPart.getAttachmentID());
                            continue;
                        }

                        if (line.startsWith("Content-ID")) {
                            currentPart.setContentID(getValue(line));
                            currentPart.setCurrentHeader(MimeHeader.CONTENT_ID);
                            System.out.println("-- Part content id " + line);
                        }

                        if (line.isEmpty() && !partBody) {
                            //begin attachment base64
                            partBody = true;
                            if (currentPart.isMultiPart()) {
                                //recursion!!
                                new PartBodyProcessor().extract(currentPart, bufferedReader);
                            }
                            continue;
                        }

                        if (partBody) {
                            if (currentPart.hasAttachment() || currentPart.isInlineImage()) {
                                if (currentPart.getFileName() == null) {
                                    currentPart.setFileName(currentPart.getFileNameFromContentDisposition());
                                }
                                byte[] decodedBytes = Base64.getDecoder().decode(line);
                                if ((this.currentSize + decodedBytes.length) > CHUNK_SIZE) {
                                    int gap = CHUNK_SIZE - this.currentSize;
                                    byte[] toFillBytes = Arrays.copyOf(decodedBytes, gap);
                                    byte[] remainBytes = Arrays.copyOfRange(decodedBytes, gap, decodedBytes.length);
                                    this.byteBuffer.put(toFillBytes, 0, gap);
                                    this.currentSize += toFillBytes.length;
                                    flushByteBuffer(currentPart, false);

                                    this.byteBuffer.put(remainBytes, 0, remainBytes.length);
                                    this.currentSize += remainBytes.length;
                                } else {
                                    this.byteBuffer.put(decodedBytes, 0, decodedBytes.length);
                                    this.currentSize += decodedBytes.length;
                                }
                                currentPart.setAttachmentSize(currentPart.getAttachmentSize() + decodedBytes.length);

                            } else if (currentPart.isTextPlainPart() || currentPart.isHTMLPart()) { //text content
                                currentPart.appendMessage(line);
                                //System.out.println("Message: " + line);
                            }
                            continue;
                        }

                        //second line of data , usually in headers
                        if (line.startsWith("\t")) {
                            currentPart.appendHeader(line.replace("\t", ""));
                            continue;
                        }
                    }
                }
            }
        }
    }
}
