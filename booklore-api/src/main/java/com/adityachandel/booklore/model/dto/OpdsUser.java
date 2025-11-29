package com.adityachandel.booklore.model.dto;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpdsUser {
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
}
