package pdfmangler;

import java.util.Iterator;
import java.util.List;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDJpeg;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDPixelMap;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectForm;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;

public class PDFMangler {
    private Config config = new Config();
    private Map<String, Float> cache;

    private PDDocument process(PDDocument doc, Map<String, Float> resolutions) throws IOException {
        cache = resolutions;
        
        List pages = doc.getDocumentCatalog().getAllPages();
        for (Object p : pages) {
            if (!(p instanceof PDPage))
                continue;
            PDPage page = (PDPage) p;
            scanResources(page.getResources(), doc);
        }
        return doc;
    }

    private void scanResources(final PDResources rList, final PDDocument doc)
            throws FileNotFoundException, IOException {
        Map<String, PDXObject> xObs = rList.getXObjects();
        for (String imageName : xObs.keySet()) {
            final PDXObject xObj = xObs.get(imageName);
            if (xObj instanceof PDXObjectForm)
                scanResources(((PDXObjectForm) xObj).getResources(), doc);
            if (!(xObj instanceof PDXObjectImage))
                continue;
            PDXObjectImage img = (PDXObjectImage) xObj;
            
            // got an image!
            
            if(config.doExtract) {
                img.write2file(imageName);
            }
            
            if(config.doInfo) {
            
                System.out.println(imageInfo(img, imageName));
            }
            
            if(config.doShrink) {
                System.out.println("Compressing image: " + imageName + " ...");
                imageShrink(doc, imageName, img);
            
            }
        }
        rList.setXObjects(xObs);
    }

    private PDXObjectImage imageShrink(final PDDocument doc, String imageName, PDXObjectImage img) throws IOException {
        
        float resolution = cache.get(imageName);

        if (resolution > config.resolutionThreshold) {

            int width = (int) (img.getWidth() * config.targetResolution / resolution);
            int height = (int) (img.getHeight() * config.targetResolution / resolution);

            System.out.println("  - resizing: " + img.getWidth() + "x" + img.getHeight()
                    + "  ->  " + width + "x" + height);

            BufferedImage image = img.getRGBImage();
            BufferedImage imageSmall = resizedImage(width, height, image);

            String suffix = img.getSuffix();

            System.out.println("  - writing back as " + suffix);

            try {

                if ("jpg".equals(suffix)) {
                    PDJpeg jpg = makeJpeg(imageSmall, doc);
                    return jpg;
                }

                if ("png".equals(suffix)) {
                    PDPixelMap png = makePng(imageSmall, doc);
                    int uncompressed = width*height*3;
                    int compressed = png.getPDStream().getLength();
                    
                    System.out.println("  - png: ratio: " + (float) compressed / uncompressed + "%  uncompressed: " + uncompressed + " compressed: " + compressed);
                    return png;
                }

            } catch (IOException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
        return img;
    }

    private String imageInfo(PDXObjectImage img, String imageName) {
        StringBuilder info = new StringBuilder();
        info.append(img.getPDStream().getLength());
        info.append("  ");
        
        info.append(img.getWidth());
        info.append("x");
        info.append(img.getHeight());
        info.append("  ");
        
        info.append(img.getSuffix());
        info.append("  ");
        
        info.append(imageName);
        
        return info.toString();
    }

    private BufferedImage resizedImage(int width, int height, Image image) {
        // get rescaled image
        Image imageSmall = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
        // render rescaled image into buffer
        BufferedImage imageSmallBuffer = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        Graphics g = imageSmallBuffer.getGraphics();
        g.drawImage(imageSmall, 0, 0, null);
        g.dispose();

        return imageSmallBuffer;
    }

    private PDPixelMap makePng(BufferedImage image, final PDDocument doc) throws IOException {
        // TODO use better compression lib here
        return new PDPixelMap(doc, image);
    }
    
    private PDJpeg makeJpeg(BufferedImage imageSmall, final PDDocument doc) throws IOException {
        final Iterator<ImageWriter> jpgWriters = ImageIO.getImageWritersByFormatName("jpeg");
        final ImageWriter jpgWriter = jpgWriters.next();
        final ImageWriteParam iwp = jpgWriter.getDefaultWriteParam();
        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        iwp.setCompressionQuality(config.jpegCompressionRatio);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        jpgWriter.setOutput(ImageIO.createImageOutputStream(baos));
        jpgWriter.write(null, new IIOImage(imageSmall, null, null), iwp);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        return new PDJpeg(doc, bais);

    }

    private static PDDocument openDocument(String fileName) throws IOException {
        final FileInputStream fis = new FileInputStream(fileName);
        final PDFParser parser = new PDFParser(fis);
        parser.parse();
        return parser.getPDDocument();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage: java PDFMangler [PDFFILE]");
            return;
            
        }
        
        System.out.println("opening file " + args[0]);
        
        PDFMangler mangler = new PDFMangler();
        
        mangler.config.inputFileName = args[0];
        mangler.config.outputFileName = "output.pdf";
        mangler.config.doInfo = true;
        mangler.config.doExtract = true;
        mangler.config.doShrink = false;
        
        
        try {

            PDDocument doc = openDocument(mangler.config.inputFileName);

            ResolutionAnalyzer occurences = new ResolutionAnalyzer();

            Map<String, Float> cache = occurences.analyze(doc);

            System.out.println("--------------------------------------------");

            doc = mangler.process(doc, cache);

            System.out.println("--------------------------------------------");

            doc.save("small.pdf");


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (COSVisitorException e) {
            e.printStackTrace();
        }
    }
}
