# 🌍 ShaiCraft Economy & Trading

**Un mod professionnel et innovant créé par Shaï pour Minecraft Fabric 1.21.1.**

ShaiCraft Economy transcende l'économie classique de Minecraft. Conçu spécifiquement pour s'intégrer avec **Cobblemon**, ce mod propose à la fois une économie de survie stable et un incroyable **simulateur de trading crypto** connecté à l'API Binance en temps réel.

---

## 🚀 Fonctionnalités Principales

### 📈 Trading Crypto Avancé (API Binance)
La plateforme de trading simule le marché de la cryptomonnaie directement dans Minecraft :
- **Flux en Temps Réel** : Les prix des cryptos s'actualisent toutes les 5 secondes via l'API Binance (BTC, ETH, etc.).
- **Levier & Liquidations** : Prenez des positions longues ou courtes (Long/Short) avec effet de levier. Si la marge ne couvre pas les pertes, une liquidation automatique s'enclenche !
- **Suivi P/L (Profit/Loss)** : Calculez vos gains et pertes en temps réel depuis le GUI.
- **Portefeuilles (Portfolio)** : Synchronisation du portefeuille crypto des joueurs à chaque connexion.

### 💰 Économie Globale & Survie
- **Monnaie Virtuelle (€)** : Économie de base performante et rapide.
- **Magasin de Survie** : Interface (`/shop`) pour l'achat et la vente de ressources à des prix fixes.
- **Investissements & Revenus Passifs** : Génération de revenus réguliers toutes les 5 minutes pour les joueurs ayant investi.
- **Minage Rémunérateur** : Système d'événements (MiningListener) qui permet de gagner des € en cassant certains blocs.

### 🐉 Intégration Cobblemon
- **HDV Pokémon** : Interface d'Hôtel de Vente (Auction) spécialement conçue pour les Pokémon Cobblemon.
- **Achat/Vente de Pokémon** : Échangez vos compagnons contre la monnaie du jeu en toute sécurité.

---

## 📜 Liste des Commandes

| Commande | Description |
| :--- | :--- |
| `/balance` | Affiche votre solde actuel (portefeuille global). |
| `/pay <joueur> <montant>` | Envoie de l'argent à un autre joueur. |
| `/shop` | Ouvre l'interface du magasin (achat/vente d'objets survie). |
| `/trading` | Ouvre la plateforme boursière crypto avec les prix Binance. |
| `/hdv` | Accède à l'hôtel de vente pour les Pokémon (Cobblemon). |
| `/trade <joueur>` | Initie un échange sécurisé (objets/argent) avec un joueur. |

---

## 🛠️ Technique et Développement

### Pré-requis 
- **Minecraft** : `1.21.1`
- **Fabric Loader** : `0.16.0` ou +
- **Java** : `21` (Requis pour compiler et jouer)

### Dépendances Requises (Mods)
- `Fabric API`
- `Fabric Language Kotlin`
- `Cobblemon (Fabric)`

### Compilation du projet
Ce mod utilise Gradle. **Attention, assurez-vous d'avoir Java 21 d'installé**, sous peine d'obtenir une erreur `Unsupported class file major version` (ex: 69 correspond à Java 25).
```bash
# Sous Windows
gradlew.bat build

# Sous macOS/Linux (si gradlew est généré)
./gradlew build
```
Le fichier `.jar` compilé se trouvera dans le dossier `build/libs/`.

### 📋 Système de Logs
Le projet intègre un système de logs complet utilisant la norme **SLF4J** de Fabric (`LOGGER.info()`). Toutes les initialisations (économie, marchés, cryptos, HDV, réseau) génèrent des traces dans les logs serveur de Minecraft, permettant de déboguer facilement le fonctionnement du backend de ShaiCraft Economy.

---
*Développé avec passion par **Shaï** — © 2026 ShaiCraft Economy*
