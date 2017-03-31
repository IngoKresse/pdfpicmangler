/*  This file is part of PDFPicMangler, an image resampling tool for pdf documents. 
 *  Copyright (C) 2017  Ingo Kresse
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package pdfpicmangler;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
    public Map<String, String> importNames = new HashMap<String,String>();
    public String pdfFileName="";
    public String outputFileName="";
    
    public void read(String[] args)
    {
        boolean gotInputFileName=false;
        
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
                    quality = Double.parseDouble(arg.substring(3));
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
                    
                    scanFiles(importPath);
                }
                else
                {
                    if (!gotInputFileName)
                    {
                        pdfFileName = arg;
                        outputFileName = pdfFileName + ".small.pdf";
                        gotInputFileName = true;
                    }
                    else
                    {
                        outputFileName = arg;
                    }
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
        
        if(!doExtract && !doStatistics && !doImport)
        {
            doShrink = true;
        }
    }
    
    private void scanFiles(String importPath)
    {
        File folder = new File(importPath);
        
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isFile()) {
                String fileName = fileEntry.getName();
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                importNames.put(baseName, fileName);
            }
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
        System.out.println("  -import=<folder> : import all images from folder");
        System.out.println("  -stats     : make statisics about every image in the pdf");
        System.out.println("  -res=<n>   : target resolution of the images in the pdf");
        System.out.println("  -resTh=<n> : only resize image if resolution is greater than this");
        System.out.println("  -q=<f>     : f=0.0 .. 1.0 quality factor for Jpeg compression");  
    }
}
