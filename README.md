# Readme

## 1. Statement on Responsible Disclosure

As detailed in the Open Science appendix of our manuscript, this artifact is provided under the principles of responsible disclosure. We face a significant ethical dilemma: the attack strategies discovered by BKEAttacker, if fully disclosed, would present an immediate and uncontrollable security risk to a vast number of users. A cooperative agreement further restricts the public release of sensitive implementation details.

Consequently, we are compelled to provide a **partial but representative** set of our artifacts. Our goal is to offer sufficient evidence to validate the efficacy, feasibility, and severity of the BKEAttacker framework, thereby allowing for rigorous peer review while simultaneously preventing the misuse of our findings. We trust this carefully curated release demonstrates our commitment to advancing scientific knowledge and protecting the public. We sincerely hope the research community will recognize the critical nature of this new attack surface affecting smart automotive PKES and related technologies.

## 2. Artifact Structure

The artifacts are organized as follows:

```
├── Attack-Demonstrations
│   ├── Detailed-Attack.mp4
│   ├── Novel_Attack_Strategy_(Non-MitM).mp4
│   ├── One-Click_Attack_Demo.mp4
│   ├── Scenario-I-TracksVictim.mp4
│   └── Scenario-Il-LuringVictim.mp4
├── Design_Motivation_Logs
├── Readme.md
└── System_Client_Side
```

## 3. Artifact Components

### **Attack-Demonstrations**

This directory contains video demonstrations that validate the effectiveness, deployability, and stealth of BKEAttacker in real-world scenarios. All sensitive information has been anonymized.

- **`Novel_Attack_Strategy_(Non-MitM).mp4`**: This video demonstrates that certain automotive PKES implementations are vulnerable to non-Man-in-the-Middle (MitM) attacks, a finding that exposes a significant gap in the industry's current understanding of PKES vulnerabilities.
- **`One-Click_Attack_Demo.mp4`**: This showcases a refined version of our attack tool. For vehicle models with a pre-identified attack strategy, it enables a "select-and-attack" operation. This dramatically lowers the barrier to execution and underscores the profound real-world threat posed by the vulnerabilities we have identified.

### **Design_Motivation_Logs**

This section provides materials to elucidate the design philosophy behind BKEAttacker, particularly our "behavioral mapping" concept.

- **`partial-hook.js`**: This script was designed to instrument key functions within the system-level BLE service on our compiled AOSP build. By observing and interfering with the interaction sequences, we were able to reverse-engineer the target PKES defense mechanisms and discover viable attack vectors. This file is intended to offer researchers new perspectives on potential attack surfaces in products employing similar defensive technologies.

### **System_Client_Side**

This directory contains a redacted version of the BKEAttacker's client-side source code. Due to the aforementioned constraints, all cloud-based code related to attack analysis and strategy generation has been omitted, and sensitive sections of the client code have been sanitized.

Despite these necessary redactions, the provided code clearly illustrates the architectural principles of our work, including the logic for BLE behavior manipulation and data communication. Our intent is to transparently demonstrate the tangible danger these vulnerabilities represent to the community.

***Note***: *The UI in this version may differ from the manuscript screenshots, as it reflects a more recent build. Furthermore, some code comments were translated from another language; we ask for your understanding regarding any minor linguistic imperfections.*



