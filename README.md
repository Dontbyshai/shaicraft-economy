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
- **Prix côté serveur** : le client ne choisit jamais le prix d'un achat ou d'une vente. Un client modifié ne peut pas acheter du bitcoin à 1 €.

### 💰 Économie Globale & Survie
- **Monnaie Virtuelle (€)** : Économie de base performante et rapide.
- **Magasin de Survie** : Interface (`/shop`) pour l'achat et la vente de ressources à des prix fixes.
- **Investissements & Revenus Passifs** : Génération de revenus réguliers toutes les 5 minutes pour les joueurs ayant investi.
- **Minage Rémunérateur** : Système d'événements (MiningListener) qui permet de gagner des € en cassant certains blocs.
- **Taxe sur `/pay`** : un pourcentage configurable (2 % par défaut) est retiré du paiement. Le destinataire reçoit le reste.
- **Historique** : `/historique` affiche les 10 dernières opérations (paiements, shop, crypto, HDV, levier, admin).
- **Classement** : `/baltop` montre les 10 plus grosses fortunes.

### 🐉 Intégration Cobblemon
- **HDV Pokémon** : Interface d'Hôtel de Vente (Auction) spécialement conçue pour les Pokémon Cobblemon.
- **Achat/Vente de Pokémon** : Échangez vos compagnons contre la monnaie du jeu en toute sécurité.

---

## 📜 Liste des Commandes

| Commande | Description |
| :--- | :--- |
| `/balance` | Affiche votre solde. Le solde d'un autre joueur (`/balance <joueur>`) est réservé aux opérateurs. |
| `/pay <joueur> <montant>` | Envoie de l'argent à un autre joueur. Une taxe peut s'appliquer. |
| `/shop` | Ouvre l'interface du magasin (achat/vente d'objets survie). |
| `/trading` | Ouvre la plateforme boursière crypto avec les prix Binance. |
| `/trading margin <montant>` | Fixe la marge du levier. `0` retire la marge personnalisée. |
| `/hdv` | Accède à l'hôtel de vente pour les Pokémon (Cobblemon). |
| `/trade <joueur>` | Initie un échange sécurisé (objets/argent) avec un joueur. |
| `/historique` | Affiche vos 10 dernières opérations. |
| `/baltop` | Classement des 10 joueurs les plus riches. |
| `/eco give <joueur> <montant>` | **Opérateur.** Ajoute de l'argent, même si le joueur est hors ligne. |
| `/eco set <joueur> <montant>` | **Opérateur.** Fixe le solde. |
| `/eco take <joueur> <montant>` | **Opérateur.** Retire de l'argent. |
| `/eco reload` | **Opérateur.** Relit `config/shaicraft-economy.json`. |

---

## 🛠️ Technique et Développement

### Pré-requis 
- **Minecraft** : `1.21.1`
- **Fabric Loader** : `0.16.0` ou +
- **Java** : `21` (Requis pour compiler et jouer)

### Dépendances Requises (Mods)
- `Fabric API`
- `Cobblemon (Fabric)`

Le mod est écrit en Java et ne dépend plus lui-même de Fabric Language Kotlin. Cobblemon, en revanche, en a toujours besoin : laissez ce mod installé à côté de Cobblemon.

### Réglages
Au premier lancement, le mod crée `config/shaicraft-economy.json` :

```json
{
  "usdToEur": 0.92,
  "payTaxPercent": 2.0,
  "autosaveSeconds": 60
}
```

- `usdToEur` : combien d'euros vaut 1 dollar Binance. `0.92` veut dire qu'un bitcoin à 60 000 $ s'affiche à 55 200 €.
- `payTaxPercent` : taxe de `/pay`, en pourcents. `2.0` = 2 %. `0` désactive la taxe. Maximum accepté : 50.
- `autosaveSeconds` : les soldes, cryptos, investissements, levier, HDV et l'historique sont écrits ensemble, au plus toutes les 60 secondes, à la déconnexion et à l'arrêt du serveur. Une commande `/eco` est écrite tout de suite.

Les fichiers de données restent dans le dossier `config/` (`shaicraft-economy-balances.json`, etc.).

### Compilation du projet
Ce mod utilise Gradle. **Java 21 doit être installé et visible** (`java -version`), sous peine d'obtenir une erreur `Unsupported class file major version`. Le script ne force plus un dossier Java précis : si besoin, définissez la variable `JAVA_HOME`.

```bash
# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

Le fichier `.jar` à mettre dans `mods/` se trouve dans `build/libs/` (pas le fichier `-sources`). Les tests (`./gradlew test`) vérifient la taxe, le classement et le refus d'un prix crypto invalide.

### 📋 Système de Logs
Le projet intègre un système de logs complet utilisant la norme **SLF4J** de Fabric (`LOGGER.info()`). Toutes les initialisations (économie, marchés, cryptos, HDV, réseau) génèrent des traces dans les logs serveur de Minecraft, permettant de déboguer facilement le fonctionnement du backend de ShaiCraft Economy.

---
Les messages de chat existent en français (`fr_fr.json`) et en anglais (`en_us.json`). Les interfaces de trading et de l'hôtel des ventes restent en français.

Licence : [MIT](LICENSE). Code : <https://github.com/Dontbyshai/shaicraft-economy>

*Développé avec passion par **Shaï** — © 2026 ShaiCraft Economy*
