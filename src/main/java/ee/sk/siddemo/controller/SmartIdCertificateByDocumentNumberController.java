package ee.sk.siddemo.controller;

/*-
 * #%L
 * Smart-ID sample Java client
 * %%
 * Copyright (C) 2018 - 2025 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import ee.sk.smartid.CertificateLevel;
import ee.sk.smartid.rest.dao.CertificateByDocumentNumberResult;
import ee.sk.siddemo.services.SmartIdCertificateByDocumentNumberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/certificate-by-document-number")
public class SmartIdCertificateByDocumentNumberController {

    private static final Logger logger = LoggerFactory.getLogger(SmartIdCertificateByDocumentNumberController.class);
    private final SmartIdCertificateByDocumentNumberService certificateService;

    public SmartIdCertificateByDocumentNumberController(SmartIdCertificateByDocumentNumberService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public String startCertificate(@RequestParam("documentNumber") String documentNumber,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            CertificateByDocumentNumberResult result = certificateService.getCertificate(documentNumber);
            model.addAttribute("documentNumber", documentNumber);
            model.addAttribute("certificate", result.certificate());
            model.addAttribute("certificateLevel", result.certificateLevel());
            model.addAttribute("distinguishedName", result.certificate().getSubjectX500Principal().getName());
            return "certificate-by-document-number-result";
        } catch (Exception e) {
            logger.error("Certificate choice failed for documentNumber {}", documentNumber, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/session-error";
        }
    }
}
