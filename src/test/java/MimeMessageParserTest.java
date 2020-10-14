import com.nguyenduyy.ExampleMimeMessageParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

public class MimeMessageParserTest {

    @Test
    public void testExampleMimeMessageParser() throws Exception {
        ExampleMimeMessageParser exampleMimeMessageParser = new ExampleMimeMessageParser("email_with_attachment.eml");
        InputStream inputStream = MimeMessageParserTest.class.getResourceAsStream(exampleMimeMessageParser.getEmailName());
        exampleMimeMessageParser.extract(inputStream);

        Set<String> fileName = exampleMimeMessageParser.getExtractedFileMap().keySet();
        Assert.assertTrue (fileName.containsAll(Arrays.asList("file_example_JPG_1MB.jpg",
                "1003 - Loan Application_Marisol Testcase.pdf",
                "Borrower Certification and Authorization .pdf",
                "file_example_PPT_1MB.ppt",
                "file_example_MP3_2MG.mp3",
                "file_example_JPG_2500kB.jpg",
                "file_example_MP4_640_3MG.mp4",
                "1003 - Loan Application_Penny Public-John Homeowner.pdf",
                "file-example_PDF_1MB.pdf")));

        String htmlContent = exampleMimeMessageParser.getMessages();

    }
}
