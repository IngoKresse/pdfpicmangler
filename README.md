=== What it is ===

The pdfpicmangler re-samples images in a pdf document to a target resolution in order to reduce file size.

The main difference to Ghostscripts ps2pdf is that this tool re-compresses losslessly encoded images lossless and jpg images as jpg. This way, no extra artifacts are introduced which are somewhat visible in high-contrast technical drawings.

pdfpicmangler also has options to export and import jpg and png in order to analyze where most memory is used and to use external compression tools. This tool supports two types of images:

JPG: The binary format of these images is almost directly embedded into PDF documents so there is no recompression loss.

PNG: PDF supports png image data as encoded in the png IDAT chunks, however:
- this type of data needs a special filter option.
- PDF does not support an alpha channel along with the image data -- it must be separate image object.
- Supported in PDF but not implemented here: Transparent color.

See command line documentation for options.
