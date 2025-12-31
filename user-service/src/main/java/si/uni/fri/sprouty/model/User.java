package si.uni.fri.sprouty.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class User {
    private String uid;
    private String email;
    private String displayName;
    private String fcmToken;

    // Firestore requires no-argument constructor
    public User() {}

    public User(String uid, String email, String displayName, String fcmToken) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.fcmToken = fcmToken;
    }

}
