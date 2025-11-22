package com.ivanka.audioeditor.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDTO {
    public long id;
    public String userName;
    public String Email;
}