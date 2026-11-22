package com.leonardogalvao.pontocontrole.employees;

import com.leonardogalvao.pontocontrole.employees.dto.CreateEmployeeMetadata;
import com.leonardogalvao.pontocontrole.employees.dto.EmployeeListResponse;
import com.leonardogalvao.pontocontrole.employees.dto.EmployeeResponse;
import com.leonardogalvao.pontocontrole.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<EmployeeResponse> create(
            @Valid @RequestPart("metadata") CreateEmployeeMetadata metadata,
            @RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(metadata, photo));
    }

    @GetMapping
    EmployeeListResponse list() {
        return employeeService.list();
    }

    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> photo(Authentication authentication, @PathVariable UUID id) {
        byte[] photo = employeeService.profilePhoto(userId(authentication), id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(photo.length)
                .cacheControl(CacheControl.noStore())
                .body(photo);
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "A autenticação é inválida.");
        }
    }
}
