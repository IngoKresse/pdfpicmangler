package pdfmangler;

public class Config {
    public boolean doInfo = false;
    public boolean doShrink = false;
    public boolean doExtract = true;

    public float resolutionThreshold = 320f;
    public float targetResolution = 300f;
    public float jpegCompressionQuality = 0.85f;

    public String inputFileName;
    public String outputFileName;
}
