package pdfpicmangler;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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

public class PDFPicMangler
{
    private Options opts = new Options();
    private Map<String, Float> resolutions;
    private int currentPage = -1;

    private PDDocument process(PDDocument doc, Map<String, Float> resolutions) throws IOException {
        this.resolutions = resolutions;
        
        List<?> pages = doc.getDocumentCatalog().getAllPages();
        for (int i = 0; i < pages.size(); i++) {
            if (!(pages.get(i) instanceof PDPage))
                continue;
            PDPage page = (PDPage) pages.get(i);
            currentPage = i + 1;
            scanResources(page.getResources(), doc);
        }
        return doc;
    }

    private void scanResources(final PDResources rList, final PDDocument doc)
            throws FileNotFoundException, IOException {
        if (rList == null) {
            return;
        }
        Map<String, PDXObject> xObs = rList.getXObjects();
        for (String imgName : xObs.keySet()) {
            final PDXObject xObj = xObs.get(imgName);
            if (xObj instanceof PDXObjectForm)
                scanResources(((PDXObjectForm) xObj).getResources(), doc);
            if (!(xObj instanceof PDXObjectImage))
                continue;
            PDXObjectImage img = (PDXObjectImage) xObj;
            String imageName = currentPage + imgName;
            
            // got an image!  
            
            if(opts.doExtract) {
                img.write2file(imageName);
            }
            
            if(opts.doStatistics) {
                System.out.println(imageInfo(img, imageName));
            }
           
			if(opts.doImport && opts.importNames.containsKey(imageName)) {
			    String path = opts.importPath;
			    String fileName = opts.importNames.get(imageName);
			    String fileNameLower = fileName.toLowerCase();
			    String fileWithPath = path + "/" + fileName;
			    
			    if(fileNameLower.endsWith(".png"))
			    {
			        System.out.println("importing " + fileWithPath + " as " + imageName + " [PNG]");
			        
			        FileInputStream is = new FileInputStream(fileWithPath);
		            img = new PDPng(doc, is);
		            xObs.put(imgName, img);
		            
		            is.close();
			    }
			    if(fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg"))
                {
			        System.out.println("importing " + fileWithPath + " as " + imageName + " [JPG]");
			        
			        FileInputStream is = new FileInputStream(path + "/" + fileName);
			        img = new PDJpeg(doc, is);
			        
			        xObs.put(imgName, img);
                    
                    is.close();
                }
			}
 
            if(opts.doShrink) {
                System.out.println("Compressing image: " + imageName + " ...");
                img = imageShrink(doc, resolutions.get(imageName), img);
                xObs.put(imgName, img);
            }
        }
        rList.setXObjects(xObs);
    }

    private PDXObjectImage imageShrink(final PDDocument doc, float resolution, PDXObjectImage img) throws IOException {
        if (resolution > opts.resolutionThreshold) {

            int width = (int) (img.getWidth() * opts.resolution / resolution);
            int height = (int) (img.getHeight() * opts.resolution / resolution);

            System.out.println("  - resizing: " + img.getWidth() + "x" + img.getHeight()
                    + "  ->  " + width + "x" + height);

            BufferedImage image = img.getRGBImage();
            BufferedImage imageSmall = resizedImage(width, height, image);

            String suffix = img.getSuffix();

            System.out.println("  - writing back as " + suffix);
            
            img.clear();
            
            int uncompressed = width*height*3;
            
            try {

                if ("jpg".equals(suffix)) {
                    PDJpeg jpg = makeJpeg(imageSmall, doc);
                    int compressed = jpg.getPDStream().getLength();
                    
                    System.out.println("  - jpg: ratio: " + (float) compressed / uncompressed + "%  uncompressed: " + uncompressed + " compressed: " + compressed);
                    
                    jpg.clear();
                    return jpg;
                }

                if ("png".equals(suffix)) {
                    PDPixelMap png = makePng(imageSmall, doc);
                    int compressed = png.getPDStream().getLength();
                    
                    System.out.println("  - png: ratio: " + (float) compressed / uncompressed + "%  uncompressed: " + uncompressed + " compressed: " + compressed);
                    
                    png.clear();
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
        info.append(" ");
        
        info.append((int) (resolutions.get(imageName).floatValue()));
        info.append(" ");
        
        info.append(img.getWidth());
        info.append("x");
        info.append(img.getHeight());
        info.append(" ");
        
        info.append(img.getSuffix());
        info.append(" ");

        int numBytes = img.getPDStream().getLength();
        int pixels = img.getWidth() * img.getHeight();
        
        info.append((800 * numBytes / pixels) / 100.0);
        info.append(" ");
        
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
    
    private PDJpeg makeJpeg(BufferedImage image, final PDDocument doc) throws IOException {     
        PDJpeg jpg = new PDJpeg(doc, image, (float) opts.quality);
        return jpg;
    }

    private static PDDocument openDocument(String fileName) throws IOException {
        final FileInputStream fis = new FileInputStream(fileName);
        final PDFParser parser = new PDFParser(fis);
        parser.parse();
        return parser.getPDDocument();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            Options.usage();
            return;
            
        }
        
        
        
        PDFPicMangler mangler = new PDFPicMangler();
        
        mangler.opts.read(args);
        
        System.out.println("opening file " + mangler.opts.pdfFileName);
        
        try {

            PDDocument doc = openDocument(mangler.opts.pdfFileName);

            ResolutionAnalyzer occurences = new ResolutionAnalyzer();

            Map<String, Float> cache = occurences.analyze(doc);

            System.out.println("--------------------------------------------");

            doc = mangler.process(doc, cache);

            System.out.println("--------------------------------------------");

            if(mangler.opts.doShrink || mangler.opts.doImport) {
                System.out.println("writing to " + mangler.opts.outputFileName);
                doc.save(mangler.opts.outputFileName);
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (COSVisitorException e) {
            e.printStackTrace();
        }
    }
}
