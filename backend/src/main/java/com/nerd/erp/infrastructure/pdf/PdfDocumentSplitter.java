package com.nerd.erp.infrastructure.pdf;

import com.nerd.erp.api.error.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfDocumentSplitter {

    public static final int MAX_PAGES = 50;

    public List<byte[]> splitIntoPages(byte[] pdfBytes) {
        try (PDDocument source = Loader.loadPDF(pdfBytes)) {
            int pageCount = source.getNumberOfPages();
            if (pageCount == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Le PDF ne contient aucune page");
            }
            if (pageCount > MAX_PAGES) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "PDF trop long (max " + MAX_PAGES + " pages par import)");
            }
            List<byte[]> pages = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                pages.add(extractPage(source, i));
            }
            return pages;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF illisible ou corrompu");
        }
    }

    private static byte[] extractPage(PDDocument source, int pageIndex) throws IOException {
        try (PDDocument singlePage = new PDDocument()) {
            PDPage page = source.getPage(pageIndex);
            singlePage.importPage(page);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            singlePage.save(out);
            return out.toByteArray();
        }
    }
}
