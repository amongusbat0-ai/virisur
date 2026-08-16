# ViriSur

Coffres protégés, accès partagés, crochetage (mini-jeux) et alarme anti-vol.

## Fonctionnalités

- Placement d'un coffre / barrel / shulker → propriétaire automatique
- Double coffre supporté
- `/add <joueur>` en regardant le coffre → accès partagé
- `/remove <joueur>` → retire l'accès
- Ouverture non autorisée → mini-jeu aléatoire (séquence / mémoire / réflexe)
- Vol réussi → alerte **tout le serveur** + **1 à 3 items** au hasard
- Cooldown **2 min** après un vol
- Alarme **5 min** : son + particules si on est proche du coffre
- Seul le propriétaire peut casser le coffre

## Commandes

| Commande | Description |
|----------|-------------|
| `/add <joueur>` | Ajouter un accès (regard sur le coffre) |
| `/remove <joueur>` | Retirer un accès |
| `/coffre` | Infos du coffre regardé |
| `/virisur reload` | Recharger la config (admin) |

## Installation

1. Build via GitHub Actions → artifact `ViriSur-jar`
2. Placer `ViriSur.jar` dans `plugins/`
3. Redémarrer le serveur

## Config

Voir `config.yml` : conteneurs, cooldown, alarme, mini-jeux, messages.
