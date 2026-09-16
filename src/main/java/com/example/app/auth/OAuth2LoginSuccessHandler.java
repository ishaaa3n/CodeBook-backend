package com.example.app.auth;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.app.folder.Folder;
import com.example.app.folder.FolderRepository;
import com.example.app.note.Note;
import com.example.app.note.NoteRepository;
import com.example.app.user.User;
import com.example.app.user.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final NoteRepository noteRepository;
    private final FolderRepository folderRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        boolean[] isNewUser = {false};

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> {
                    isNewUser[0] = true;
                    User newUser = User.builder()
                            .googleId(googleId)
                            .email(email)
                            .name(name)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(newUser);
                });

        if (isNewUser[0]) {
            seedSampleData(user);
        }

        String token = jwtUtil.generateToken(user.getId());
        response.sendRedirect(frontendUrl + "/auth/callback?token=" + token + "&name=" + java.net.URLEncoder.encode(name, "UTF-8"));
    }

    private void seedSampleData(User user) {
    Folder folder1 = folderRepository.save(Folder.builder()
            .name("My Projects")
            .user(user)
            .createdAt(LocalDateTime.now())
            .build());
    System.out.println(">>> Saved folder1: " + folder1.getId());

    Folder folder2 = folderRepository.save(Folder.builder()
            .name("Practice")
            .user(user)
            .createdAt(LocalDateTime.now())
            .build());
    System.out.println(">>> Saved folder2: " + folder2.getId());

    noteRepository.save(Note.builder()
            .title("Hello World")
            .content("print('Hello, World!')")
            .language("python")
            .input("")
            .folder(folder1)
            .user(user)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    System.out.println(">>> Saved note1 in My Projects");

    noteRepository.save(Note.builder()
        .title("Binary Search")
        .content("def binary_search(arr, target):\n    left, right = 0, len(arr) - 1\n    while left <= right:\n        mid = (left + right) // 2\n        if arr[mid] == target:\n            return mid\n        elif arr[mid] < target:\n            left = mid + 1\n        else:\n            right = mid - 1\n    return -1\n\n# Example usage\narr = [1, 3, 5, 7, 9, 11]\nprint(binary_search(arr, 7))  # Output: 3\nprint(binary_search(arr, 4))  # Output: -1")
        .language("python")
        .input("")
        .folder(folder2)
        .user(user)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build());
    System.out.println(">>> Saved note2 in Practice");

    noteRepository.save(Note.builder()
            .title("Untitled")
            .content("// Start coding here")
            .language("javascript")
            .input("")
            .folder(null)
            .user(user)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    System.out.println(">>> Saved note3 unfiled");
}
}