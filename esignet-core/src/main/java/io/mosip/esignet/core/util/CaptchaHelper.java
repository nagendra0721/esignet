/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.CaptchaRequest;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CAPTCHA;

@Slf4j
public class CaptchaHelper {

    private RestTemplate restTemplate;
    private String moduleName;
    private String validatorUrl;

    private List<String> captchaRequired;

    public CaptchaHelper(RestTemplate restTemplate, String validatorUrl, String moduleName, List<String> captchaRequired) {
        this.restTemplate = restTemplate;
        this.validatorUrl = validatorUrl;
        this.moduleName = moduleName;
        this.captchaRequired = captchaRequired;
    }

    public void validateCaptchaToken(String captchaToken, String authFactor) {
        if(!captchaRequired.contains(authFactor)) {
            log.warn("captcha validation is disabled for {} request!", authFactor);
            return;
        }
        if(!StringUtils.hasText(captchaToken)) {
            log.error("Captcha token is Null or Empty");
            throw new EsignetException(INVALID_CAPTCHA);
        }
        if (!validateCaptcha(captchaToken))
            throw new EsignetException(INVALID_CAPTCHA);
    }


    public boolean validateCaptcha(String captchaToken) {

        if (captchaToken == null || captchaToken.isBlank()) {
            throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
        }

        try{
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            CaptchaRequest captchaRequest = new CaptchaRequest();
            captchaRequest.setCaptchaToken(captchaToken);
            captchaRequest.setModuleName(moduleName);

            RequestWrapper<CaptchaRequest> requestWrapper = new RequestWrapper<>();
            requestWrapper.setRequestTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
            requestWrapper.setRequest(captchaRequest);
            RequestEntity<RequestWrapper<CaptchaRequest>> requestEntity = RequestEntity
                .post(URI.create(validatorUrl))
                .headers(headers)
                .body(requestWrapper);

            ResponseEntity<ResponseWrapper> responseEntity = restTemplate.exchange(
                    requestEntity,
                    ResponseWrapper.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                ResponseWrapper<?> responseWrapper = (ResponseWrapper<?>) responseEntity.getBody();
                if (responseWrapper != null && responseWrapper.getResponse() != null &&
                        CollectionUtils.isEmpty(responseWrapper.getErrors())) {
                    log.info("Captcha Validation Successful");
                    return true;
                }
                log.error("Errors received from CaptchaService: {}", responseEntity.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to validate captcha", e);
        }
        throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
    }

}