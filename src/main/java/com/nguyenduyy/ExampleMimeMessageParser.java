package com.nguyenduyy;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ExampleMimeMessageParser extends MimeMessageStreamParser {
    Map<String, ExtractFile> extractedFileMap = new HashMap<>();
    public ExampleMimeMessageParser(String emailName) {
        super(emailName);

        /*
         * Register Inline Image handler ***
         */
        setInlineImageHandler(new InlineImageHandler() {
            @Override
            public String execute(String fileName, byte[] image, boolean last) throws Exception {
                writeFile(fileName, image, last);
                return null;
            }
        });

        /*
         * Register Attachment handler ***
         */
        setOnReceiveBytes(new AttachmentHandler() {
            @Override
            public void execute(byte[] data, int length, Part currentPart, boolean last) throws Exception {
                String fileName = currentPart.getFileName();
                writeFile(fileName, data, last);
            }
        });
    }

    private void writeFile(String fileName, byte[] data, boolean last) throws IOException {
        if (data.length > 0) {
            extractedFileMap.putIfAbsent(fileName, new ExtractFile(File.createTempFile(fileName, "")));
            extractedFileMap.get(fileName).getFileOutputStream().write(data);
            if (last) {
                extractedFileMap.get(fileName).done();
            }
        }
    }

    public Map<String, ExtractFile> getExtractedFileMap() {
        return extractedFileMap;
    }

    @Override
    public void store(InputStream in) throws Exception {
        File file = new File(this.getEmailName());
        IOUtils.copy(in, new FileOutputStream(file));
    }

    @Override
    public InputStream retrieve() throws Exception {
        return new FileInputStream(new File(this.getEmailName()));
    }

    class ExtractFile {
        FileOutputStream fileOutputStream;
        File file;

        public ExtractFile(File file) throws FileNotFoundException {
            this.file = file;
            this.fileOutputStream = new FileOutputStream(file);
        }

        public FileOutputStream getFileOutputStream() {
            return fileOutputStream;
        }

        public File getFile() {
            return file;
        }

        public void done() throws IOException {
            this.fileOutputStream.close();
        }
    }
}
