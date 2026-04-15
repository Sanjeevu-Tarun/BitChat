# 🚀 BitChat – Advanced Offline Bluetooth Chat App
> A powerful, fully offline messaging application built using **Kotlin + Jetpack Compose**, enabling seamless communication over Bluetooth — no internet required.

---

## 📱 Overview

**BitChat** is a next-generation offline chat application that allows users to send messages, files, and media over **Bluetooth Classic** without relying on Wi-Fi or internet connectivity.

Designed with a **modern UI**, **real-time communication**, and **secure data handling**, BitChat is ideal for:

- 🔌 No-network environments  
- 🎓 Campus communication  
- 🏕️ Outdoor / remote usage  
- 🔒 Private, decentralized messaging  

---

## ✨ Features

### 💬 Core Messaging
- 📡 Real-time Bluetooth messaging  
- 🔄 Instant send & receive  
- 🧵 Persistent chat sessions  
- 🕒 Message timestamps  

### 📂 File Sharing
- 📁 Send images, documents, and files  
- ⚡ Fast byte-stream transfer  
- 📊 Transfer progress tracking  

### 🔐 Security
- 🔑 Message encryption support  
- 🛡️ Secure device-to-device communication  

### 📡 Bluetooth Capabilities
- 🔍 Discover nearby devices  
- 🔗 Pair & connect seamlessly  
- 📶 Signal strength indication  

### 🎨 UI/UX
- 🧩 Built with Jetpack Compose  
- 🌙 Clean & modern design  
- ⚡ Smooth animations & transitions  

---

## 🏗️ Tech Stack

| Layer        | Technology            |
|--------------|-----------------------|
| Language     | Kotlin                |
| UI           | Jetpack Compose       |
| Architecture | MVVM                  |
| Database     | Room                  |
| Bluetooth    | Android Bluetooth API |
| Async        | Coroutines + Flow     |

---

## 📂 Project Structure

```
com.bitchat.app
├── data/
│   ├── local/          (Room DB, DAO)
│   └── repository/
│
├── ui/
│   ├── screens/
│   └── components/
│
├── bluetooth/
│   ├── BluetoothRepository.kt
│   └── BluetoothService.kt
│
├── viewmodel/
│
└── utils/
```

---

## 🚀 Getting Started

### 🔧 Prerequisites

- Android Studio (Latest)
- Android device with Bluetooth support
- Minimum SDK: 26+

### ▶️ Installation

1. Clone the repository:

```bash
git clone https://github.com/your-username/BitChat.git
cd BitChat
```

2. Open the project in Android Studio  
3. Build & Run the app 🚀  



---

## ⚠️ Permissions Required

- 📡 Bluetooth  
- 📍 Location (for device discovery)  
- 📂 Storage (for file sharing)  

---

## 🧠 Future Enhancements

- 🌐 Bluetooth Low Energy (BLE) support  
- 📶 Multi-device group chat  
- 🔔 Push-style local notifications  
- ☁️ Optional Wi-Fi Direct support  

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repo  
2. Create your feature branch  
3. Commit your changes  
4. Open a Pull Request  

---

## 📜 License

This project is licensed under the MIT License.

---

## ⭐ Support

If you like this project:

- ⭐ Star the repo  
- 🍴 Fork it  
- 🧑‍💻 Share with others  

---

## 👨‍💻 Author

**Sanjeevu Tarun Sree Prasad**

---

> ⚡ Built with passion to enable communication without limits.
