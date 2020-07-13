/**
 * DynamicReports - Free Java reporting library for creating reports dynamically
 *
 * Copyright (C) 2010 - 2016 Ricardo Mariaca
 * http://www.dynamicreports.org
 *
 * This file is part of DynamicReports.
 *
 * DynamicReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DynamicReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with DynamicReports. If not, see <http://www.gnu.org/licenses/>.
 */

package Reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import net.sf.jasperreports.engine.JRGenericPrintElement;
import net.sf.jasperreports.engine.export.GenericElementPdfHandler;
import net.sf.jasperreports.engine.export.JRPdfExporterContext;

import org.apache.commons.collections.ReferenceMap;

import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfAnnotation;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfFileSpecification;
import com.lowagie.text.pdf.PdfIndirectObject;
import com.lowagie.text.pdf.PdfIndirectReference;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNameTree;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;

/**
 * @author Ricardo Mariaca (r.mariaca@dynamicreports.org)
 */
@SuppressWarnings("deprecation")
public class OpenFlashChartPdfHandler implements GenericElementPdfHandler {

	private final ReferenceMap existingContexts = new ReferenceMap(ReferenceMap.WEAK,	ReferenceMap.HARD);

	@Override
	public boolean toExport(JRGenericPrintElement element) {
		return true;
	}

	@Override
	public void exportElement(JRPdfExporterContext exporterContext,	JRGenericPrintElement element) {
		try	{
			PdfWriter writer = exporterContext.getPdfWriter();
			PdfIndirectObject swfRef;
			boolean newContext = !existingContexts.containsKey(exporterContext);
			if (newContext) {
				PdfDictionary extensions = new PdfDictionary();
				PdfDictionary adobeExtension = new PdfDictionary();
				adobeExtension.put(new PdfName("BaseVersion"), PdfWriter.PDF_VERSION_1_7);
				adobeExtension.put(new PdfName("ExtensionLevel"), new PdfNumber(3));
				extensions.put(new PdfName("ADBE"), adobeExtension);
				writer.getExtraCatalog().put(new PdfName("Extensions"), extensions);

				byte[] swfData = getChartSwf();
				PdfFileSpecification swfFile = PdfFileSpecification.fileEmbedded(writer, null, "Open Flash Chart", swfData);
				swfRef = writer.addToBody(swfFile);
				existingContexts.put(exporterContext, swfRef);
			}
			else {
				swfRef = (PdfIndirectObject) existingContexts.get(exporterContext);
			}

			Rectangle rect = new Rectangle(element.getX() + exporterContext.getOffsetX(),
					exporterContext.getExportedReport().getPageHeight() - element.getY() - exporterContext.getOffsetY(),
					element.getX() + exporterContext.getOffsetX() + element.getWidth(),
					exporterContext.getExportedReport().getPageHeight() - element.getY() - exporterContext.getOffsetY() - element.getHeight());
			PdfAnnotation ann = new PdfAnnotation(writer, rect);
			ann.put(PdfName.SUBTYPE, new PdfName("RichMedia"));

			PdfDictionary settings = new PdfDictionary();
			PdfDictionary activation = new PdfDictionary();
			activation.put(new PdfName("Condition"), new PdfName("PV"));
			settings.put(new PdfName("Activation"), activation);
			ann.put(new PdfName("RichMediaSettings"), settings);

			PdfDictionary content = new PdfDictionary();

			HashMap<String, PdfIndirectReference> assets = new HashMap<String, PdfIndirectReference>();
			assets.put("map.swf", swfRef.getIndirectReference());
			PdfDictionary assetsDictionary = PdfNameTree.writeTree(assets, writer);
			content.put(new PdfName("Assets"), assetsDictionary);

			PdfArray configurations = new PdfArray();
			PdfDictionary configuration = new PdfDictionary();

			PdfArray instances = new PdfArray();
			PdfDictionary instance = new PdfDictionary();
			instance.put(new PdfName("Subtype"), new PdfName("Flash"));
			PdfDictionary params = new PdfDictionary();

			String chartData = ((ChartGenerator) element.getParameterValue(ChartGenerator.PARAMETER_CHART_GENERATOR)).generateChart();
			String vars = "inline_data=" + chartData;
			params.put(new PdfName("FlashVars"), new PdfString(vars));
			instance.put(new PdfName("Params"), params);
			instance.put(new PdfName("Asset"), swfRef.getIndirectReference());
			PdfIndirectObject instanceRef = writer.addToBody(instance);
			instances.add(instanceRef.getIndirectReference());
			configuration.put(new PdfName("Instances"), instances);

			PdfIndirectObject configurationRef = writer.addToBody(configuration);
			configurations.add(configurationRef.getIndirectReference());
			content.put(new PdfName("Configurations"), configurations);

			ann.put(new PdfName("RichMediaContent"), content);

			writer.addAnnotation(ann);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] getChartSwf() throws IOException {
		InputStream is = OpenFlashChartPdfHandler.class.getResourceAsStream("open-flash-chart.swf");
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = is.read(data, 0, data.length)) != -1) {
		  buffer.write(data, 0, nRead);
		}

		buffer.flush();

		return buffer.toByteArray();

	}
}
