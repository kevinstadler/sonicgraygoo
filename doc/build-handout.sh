#!/bin/sh
TARGET=handout.md
if [ ! -d "qr" ]; then
  mkdir "qr"
fi
echo '---
classoption: a5paper,10pt
header-includes:
  - \\renewcommand{\\familydefault}{\\sfdefault}
  - \\renewcommand{\\UrlFont}{\\footnotesize}
  - \usepackage{array}
  - \\newcolumntype{V}{>{\\centering\\arraybackslash} m{.4\\linewidth} }
  - \usepackage{graphicx}
  - \usepackage{geometry}
  - \\geometry{left=1in,right=1in}
  - \usepackage{setspace}
  - \\doublespacing
  - \\pagenumbering{gobble}
---
# sonic gray goo' > "$TARGET"
cat handout.txt >> "$TARGET"
# \small{more links to reading material and videos/documentaries on the back of this sheet\n\n
echo '\\newpage\n\n# recommended video/film
\\singlespacing\\vspace{-0.7in}
\\begin{tabular}{m{2.75in}V}' >> "$TARGET"
i=0
while read line; do
  if [ -n "$line" ]; then
    echo "$line"
    case "$line" in
      http*)
        i=$((i+1))
        qrencode -o "qr/$i.eps" --type=EPS "$line"
        epspdf "qr/$i.eps"
        echo '\n\\url{' >> "$TARGET"
        echo "$line} & \\includegraphics[width=1in]{qr/$i.pdf}" >> "$TARGET"
        ;;
      *)
        echo '\\\\\\\\' >> "$TARGET"
        echo "$line" >> "$TARGET"
        ;;
    esac
  fi
done < further-watching.txt
echo '\\end{tabular}\n\n# provided reading
\\singlespacing\\vspace{-0.7in}
\\begin{tabular}{m{2.75in}V}' >> "$TARGET"
while read line; do
  if [ -n "$line" ]; then
    echo "$line"
    case "$line" in
      http*)
        i=$((i+1))
        qrencode -o "qr/$i.eps" --type=EPS "$line"
        epspdf "qr/$i.eps"
        echo '\n\\url{' >> "$TARGET"
        echo "$line} & \\includegraphics[width=1in]{qr/$i.pdf}" >> "$TARGET"
        ;;
      *)
        echo '\\\\\\\\' >> "$TARGET"
        echo "$line" >> "$TARGET"
        ;;
    esac
  fi
done < provided-reading.txt
echo '\\end{tabular}\\newpage\n\n# recommended reading
\\singlespacing\\vspace{-0.7in}
\\begin{tabular}{m{2.5in}V}' >> "$TARGET"
while read line; do
  if [ -n "$line" ]; then
    echo "$line"
    case "$line" in
      http*)
        i=$((i+1))
        qrencode -o "qr/$i.eps" --type=EPS "$line"
        epspdf "qr/$i.eps"
        echo '\n\\url{' >> "$TARGET"
        echo "$line} & \\includegraphics[width=1in]{qr/$i.pdf}" >> "$TARGET"
        ;;
      *)
        echo '\\\\\\\\' >> "$TARGET"
        echo "$line" >> "$TARGET"
        ;;
    esac
  fi
done < further-reading.txt
echo '\\end{tabular}
\\newpage\\vspace*{\\fill}\\footnotesize
thoughts/questions/comments? kevin.stadler@ed.ac.uk\n\n
get a goo for your own home: https://github.com/kevinstadler/sonicgraygoo' >> "$TARGET"
Rscript -e "rmarkdown::render('$TARGET', rmarkdown::pdf_document(fig_width=2, fig_height=2))"

