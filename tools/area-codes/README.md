# Where-from data (area codes and map)

`generate.py` builds three assets in `app/src/main/assets/`. Don't edit them by hand; run the
script again instead.

| Asset | What | Source |
|---|---|---|
| `bc_area_codes.csv` | North American area code → state/province (`OH`, `NS/PE`) or country | [libphonenumber](https://github.com/google/libphonenumber) `resources/geocoding/en/1.txt`, Apache License 2.0 |
| `bc_regions.csv` | State/province code → name | Natural Earth admin-1, public domain |
| `bc_map.bin` | Simplified state/province outlines (about 15 KB) | [Natural Earth](https://www.naturalearthdata.com/) `ne_50m_admin_1_states_provinces_lakes`, public domain |

Codes outside the US and Canada (Jamaica, Puerto Rico, …) are a table in the script.

```bash
python3 -I tools/area-codes/generate.py 1.txt ne_50m_admin_1_states_provinces_lakes.geojson app/src/main/assets
```

An area code says where a number was **issued**, not where the caller is.
