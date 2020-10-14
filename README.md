# MimeMessageStreamParser
Parse MimeMessage in stream mode instead of load the whole mime message into the memory which can cause OutOfMemory exception

Take a look at ```ExampleMimeMessageParser.java``` && ```MimeMessageParserTest.java``` for example usage.

Technically, the MimeMessageStreamParser reads a mime message by ```readLine()```, decode and append to mime parts.

### API

#### Register Inline Image Handler 
```
setInlineImageHandler(new InlineImageHandler() {
               @Override
               public String execute(String fileName, byte[] image, boolean last) throws Exception {
                   ...
                   return ...; 
               }
           });
```

#### Register Attachment Handler
```
setOnReceiveBytes(new AttachmentHandler() {
            @Override
            public void execute(byte[] data, int length, Part currentPart, boolean last) throws Exception {
                ...
            }
        });
```
#### Get main message
```getMessages()``` : Get HTML content of the mime message. Inline images will be replaced by returned values of ```InlineImageHandler```(s)