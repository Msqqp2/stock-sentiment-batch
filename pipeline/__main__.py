"""python -m pipeline 으로 실행 가능하도록 하는 엔트리포인트."""

from pipeline.daily_sync import main

if __name__ == "__main__":
    main()
