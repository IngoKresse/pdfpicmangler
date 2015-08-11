package pdfmangler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectForm;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDXObjectImage;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.PDFOperator;
import org.apache.pdfbox.util.PDFStreamEngine;
import org.apache.pdfbox.util.ResourceLoader;

public class OccurenceAnalyzer extends PDFStreamEngine {

	private static final String INVOKE_OPERATOR = "Do";

	private ImageInfoCache cache = new ImageInfoCache();

	/**
	 * Default constructor.
	 * 
	 * @throws IOException
	 *             If there is an error loading text stripper properties.
	 */
	public OccurenceAnalyzer() throws IOException {
		super(ResourceLoader.loadProperties(
				"org/apache/pdfbox/resources/PDFTextStripper.properties", true));
	}

	ImageInfoCache analzye(PDDocument document) throws IOException {
		cache.clear();

		List allPages = document.getDocumentCatalog().getAllPages();
		for (int i = 0; i < allPages.size(); i++) {
			PDPage page = (PDPage) allPages.get(i);
			System.out.println("Processing page: " + i);
			processStream(page, page.findResources(), page.getContents()
					.getStream());
		}

		return cache;
	}

	protected void processOperator(PDFOperator operator, List arguments)
			throws IOException {
		String operation = operator.getOperation();
		if (INVOKE_OPERATOR.equals(operation)) {
			COSName objectName = (COSName) arguments.get(0);
			Map<String, PDXObject> xobjects = getResources().getXObjects();
			PDXObject xobject = (PDXObject) xobjects.get(objectName.getName());
			if (xobject instanceof PDXObjectImage) {
				PDXObjectImage image = (PDXObjectImage) xobject;
				PDPage page = getCurrentPage();
				int imageWidth = image.getWidth();
				int imageHeight = image.getHeight();
				double pageHeight = page.getMediaBox().getHeight();
				System.out
						.println("*******************************************************************");
				System.out
						.println("Found image [" + objectName.getName() + "]");

				Matrix ctmNew = getGraphicsState()
						.getCurrentTransformationMatrix();

				float imageXScale = ctmNew.getXScale() / 72;
				float imageYScale = ctmNew.getYScale() / 72;
				// size in pixel
				System.out.println("  size: " + imageWidth + " x "
						+ imageHeight + " px");
				// size in page units
				System.out.print("  size_on_page: " + imageXScale + " x "
						+ imageYScale + " in ");
				System.out.println("( = " + (imageXScale * 25.4) + " x "
						+ (imageYScale * 25.4) + " mm)");
				System.out.println("  dpi: " + (imageWidth / imageXScale)
						+ " x " + (imageHeight / imageYScale) + " dpi");
				System.out.println();

				System.out.println("  filters: "
						+ image.getPDStream().getFilters());
				System.out.println("  compressed_size: "
						+ image.getPDStream().getLength());

				COSBase result = page.getCOSDictionary().getDictionaryObject(
						"UserUnit");
				System.out.println("  userunit: " + result);

				float dpiX = imageWidth / imageXScale;
				float dpiY = imageHeight / imageYScale;
				float dpi = 0.5f * (dpiX + dpiY);

				if (Math.abs((dpiX - dpiY) / dpi) > 0.05) {
					System.out.println("warning: resolution of image "
							+ objectName.getName() + "is not square: dpiX="
							+ dpiX + " dpiY=" + dpiY);
				}

				cache.insert(objectName.getName(), dpi);
			} else if (xobject instanceof PDXObjectForm) {
				// save the graphics state
				getGraphicsStack().push(
						(PDGraphicsState) getGraphicsState().clone());
				PDPage page = getCurrentPage();

				PDXObjectForm form = (PDXObjectForm) xobject;
				PDResources pdResources = form.getResources();
				if (pdResources == null) {
					pdResources = page.findResources();
				}
				// if there is an optional form matrix, we have to
				// map the form space to the user space
				Matrix matrix = form.getMatrix();
				if (matrix != null) {
					Matrix xobjectCTM = matrix.multiply(getGraphicsState()
							.getCurrentTransformationMatrix());
					getGraphicsState().setCurrentTransformationMatrix(
							xobjectCTM);
				}
				processSubStream(page, pdResources,
						(COSStream) form.getCOSObject());

				// restore the graphics state
				setGraphicsState((PDGraphicsState) getGraphicsStack().pop());
			}

		} else {
			super.processOperator(operator, arguments);
		}
	}
}
