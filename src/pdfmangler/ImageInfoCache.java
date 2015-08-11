package pdfmangler;

import java.util.HashMap;
import java.util.Map;

public class ImageInfoCache
{
	
	private Map<String, Float> resolutions = new HashMap<String, Float>();
	
	public void insert(String imageName, float resolution)
	{
		if(resolutions.containsKey(imageName))
		{
			System.out.println("reused image name: " + imageName);
		}
		resolutions.put(imageName, resolution);
	}
	
	public float getResolution(String imageName)
	{
		if(!resolutions.containsKey(imageName))
		{
			System.out.println("unused image : " + imageName);
			return -1;
		}
		return resolutions.get(imageName);
	}

	public void clear() {
		resolutions.clear();
	}
}
