# LkQwen
D-Smart A8U

## LK Qwen Android – 0.1.0 alpha

Yerel Qwen3.5-2B Q4_K_M, ARM64 llama.cpp motoru. Bu ilk sürüm henüz fiziksel Android cihazda test edilmemiştir.

### APK
[Actions](https://github.com/lkbilisim/LkQwen/actions) → LK Qwen Android APK → başarılı derleme → Artifacts → LKQwen-Android-ARM64 ZIP → APK.

Model APK içinde değildir. İlk kurulumda uygulamadan yaklaşık 1,28 GB GGUF indirilebilir (SHA-256 doğrulamalı) veya mevcut GGUF içe aktarılabilir. Sohbetler cihaza özel alanda tutulur. Model çalışırken yalnızca 127.0.0.1 üzerinde, oturuma özel anahtar ile yerel motor kullanılır; dışarıya sohbet gönderilmez.

İlk profil: 2048 bağlam, 4 CPU iş parçacığı, 384 yanıt tokeni. Ses, telefon otomasyonu, gerçek kurumsal logo, gelişmiş kalıcı hafıza ve kararlı sürüm imzalama henüz eklenmedi. CI debug APK imzaları farklı derlemelerde değişebilir; güncelleme için sabit özel imza anahtarı gerekir.

Motor: [llama.cpp b12249](https://github.com/ggml-org/llama.cpp/releases/tag/b12249) (MIT).
Model: [Unsloth Qwen3.5-2B GGUF](https://huggingface.co/unsloth/Qwen3.5-2B-GGUF), Qwen model lisansı için model deposuna bakın.
