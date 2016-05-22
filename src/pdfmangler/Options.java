package pdfmangler;

public class Options
{
    public double resolution=300;
    public double resolutionThreshold=450;
    public double quality=0.85;
    
    public boolean doExtract;
    public boolean doImport;
    public boolean doStatistics;
    public boolean doShrink;
    
    public String importPath=".";
    public String pdfFileName="";
    
    public void read(String[] args)
    {
        for(String arg: args)
        {
            try
            {
                if(arg.startsWith("-res="))
                {
                    resolution = Double.parseDouble(arg.substring(5));
                }
                else if(arg.startsWith("-resTh="))
                {
                    resolutionThreshold = Double.parseDouble(arg.substring(7));
                }
                else if(arg.startsWith("-q="))
                {
                    resolutionThreshold = Double.parseDouble(arg.substring(3));
                }
                else if(arg.equals("-extract"))
                {
                    doExtract = true;
                }
                else if(arg.equals("-stats"))
                {
                    doStatistics = true;
                }
                else if(arg.startsWith("-import="))
                {
                    doImport = true;
                    importPath = arg.substring(8);
                }
                else
                {
                    pdfFileName = arg;
                }
            }
            catch(IndexOutOfBoundsException e)
            {
                continue;
            }
            catch(NumberFormatException e)
            {
                continue;
            }
        }
        
        if(!doExtract && !doStatistics)
        {
            doShrink = true;
        }
    }
    
    public String toString()
    {
        return "res=" + resolution + " resTh=" + resolutionThreshold + " q=" + quality;
    }

    public static void usage()
    {
        System.out.println("usage: java PDFMangler [options] [PDFFILE]");
        System.out.println("Options:");
        System.out.println("  -extract   : extract all images to files");
        System.out.println("  -stats     : make statisics about every image in the pdf");
        System.out.println("  -res=<n>   : target resolution of the images in the pdf");
        System.out.println("  -resTh=<n> : only resize image if resolution is greater than this");
        System.out.println("  -q=<f>     : f=0.0 .. 1.0 quality factor for Jpeg compression");  
    }
}
