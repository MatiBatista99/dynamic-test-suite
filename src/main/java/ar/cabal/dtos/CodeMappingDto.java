package ar.cabal.dtos;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeMappingDto {

    private String code;

    private String description;

}
