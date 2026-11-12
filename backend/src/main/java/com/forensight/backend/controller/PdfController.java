package com.forensight.backend.controller;


import com.forensight.backend.model.ForensicReport;
import com.forensight.backend.service.PdfService;


import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/pdf")
public class PdfController {



private final PdfService pdfService;



public PdfController(
        PdfService pdfService
){

    this.pdfService = pdfService;

}





@PostMapping("/report")
public ResponseEntity<byte[]> downloadPdf(
        @RequestBody ForensicReport report
){



byte[] pdf =
        pdfService.generateReportPdf(report);



return ResponseEntity.ok()

.header(
HttpHeaders.CONTENT_DISPOSITION,
"attachment; filename=Forensic_Report.pdf"
)

.contentType(
MediaType.APPLICATION_PDF
)

.body(pdf);



}


}