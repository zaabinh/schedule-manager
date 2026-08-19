package vn.edu.school.schedule.auth.api;

public record LoginResult(CurrentUser user, String sessionToken, String csrfToken) { }
